// test816b : project USAG metrics
package com.example.k7metrics;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.function.Consumer;

public class Notebook {
    private static volatile Object DUMMY;
    private static void sclear(byte[] data) {
        java.util.Arrays.fill(data, (byte) 0);
        DUMMY = data;
    }
    public static void ClearDummy() {
        Bencrypt.ClearDummy();
        Opsec.ClearDummy();
        DUMMY = null;
    }

    // Notebook singleton generation
    private static Notebook instance;
    private Context context;
    public static synchronized Notebook getInstance(Context c) throws Exception {
        if (instance == null) instance = new Notebook(c.getApplicationContext());
        return instance;
    }
    private Notebook(Context c) throws Exception {
        this.context = c;
        IO1.VFile a = IO1.GetLocal(this.context, "account.bin");
        if (a.Exists(this.context)) {
            this.LoadAccount(); // load account.bin
        } else {
            // set account, password
            this.Rate = 1400;
            this.ScreenPW = BASIC_PW;
            this.Msg = BASIC_PW;
            Bencrypt b = new Bencrypt();
            this.masterKey = b.Random(64);
            this.SetPW(BASIC_PW, null); // need few seconds to save file

            // set texts
            this.Texts = new HashMap<String, String>();
            this.Texts.put("New Notebook", "Welcome!");
            this.SaveTexts();

            // set names
            for (int i = 0; i < 8; i++) {
                this.LoadNames(i);
                this.SaveNames(i);
            }
        }
    }

    // account field
    public int Rate;
    public String ScreenPW;
    public String Msg;
    public byte[] Salt;
    public byte[] PwHash;
    public byte[] EncMasterKey;
    public void LoadAccount() throws Exception {
        // open account.bin and read data
        IO1.VFile a = IO1.GetLocal(this.context, "account.bin");
        if (!a.Exists(this.context)) return;
        Opsec ops = new Opsec();
        byte[] data;
        try (java.io.InputStream is = a.OpenReader(this.context)) {
            data = ops.Read(is, 0);
        }
        if (data.length == 0) return;

        // decode to fields
        java.util.Map<String, byte[]> acc = ops.DecodeCfg(data);
        if (acc.containsKey("rate")) this.Rate = (int) ops.DecodeInt(acc.get("rate"));
        if (acc.containsKey("spw")) this.ScreenPW = new String(acc.get("spw"), StandardCharsets.UTF_8);
        if (acc.containsKey("msg")) this.Msg = new String(acc.get("msg"), StandardCharsets.UTF_8);
        if (acc.containsKey("salt")) this.Salt = acc.get("salt");
        if (acc.containsKey("pwh")) this.PwHash = acc.get("pwh");
        if (acc.containsKey("emk")) this.EncMasterKey = acc.get("emk");

        ClearDummy();
    }
    public void SaveAccount() throws Exception {
        // make encoded account data
        HashMap<String, byte[]> acc = new HashMap<String, byte[]>();
        Opsec ops = new Opsec();
        acc.put("rate", ops.EncodeInt(this.Rate, 4));
        acc.put("spw", Bencode.NormPW(this.ScreenPW));
        acc.put("msg", this.Msg.getBytes(StandardCharsets.UTF_8));
        acc.put("salt", this.Salt);
        acc.put("pwh", this.PwHash);
        acc.put("emk", this.EncMasterKey);
        byte[] encoded = ops.EncodeCfg(acc);

        // write to account.bin
        IO1.VFile a = IO1.GetLocal(this.context, "account.bin");
        try (java.io.OutputStream os = a.OpenWriter(this.context, false)) {
            ops.Write(os, encoded);
        }

        ClearDummy();
    }

    // access field
    public static final String BASIC_PW = "0000";
    public static final String METHOD_HASH = "arg2";
    public static final String METHOD_HASH_INTERNAL = "sha3";
    public static final String METHOD_SYM = "gcmx1";
    public static final String METHOD_SYM_INTERNAL = "gcm1";
    private Bencrypt.Masker mask = Bencrypt.Masker.GetMasker();
    private byte[] masterKey; // masked
    public void Login(String pw, Consumer<Exception> callback) {
        new Thread(() -> {
            try {
                // Argon2 KDF
                byte[] pwb = Bencode.NormPW(pw);
                Bencrypt.HashMaster hm = new Bencrypt.HashMaster(METHOD_HASH);
                byte[][] res = hm.KDF(pwb, this.Salt);
                sclear(pwb);
                if (!Arrays.equals(res[0], this.PwHash)) { // pwh is public value
                    throw new SecurityException("invalid password");
                }

                // get masterkey
                Bencrypt.SymMaster sm = new Bencrypt.SymMaster(METHOD_SYM_INTERNAL, res[1]);
                sclear(res[1]);
                byte[] mk = sm.DeBin(this.EncMasterKey);
                this.masterKey = this.mask.XOR(mk);
                sclear(mk);

                // load Texts and Names
                this.LoadTexts();
                for (int i = 0; i < 8; i++) this.LoadNames(i);

                // send main null(success)
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.accept(null));
                }
            } catch (Exception e) {
                // send main error(fail)
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.accept(e));
                }
            }
            ClearDummy();
        }).start();
    }
    public void SetPW(String pw, Consumer<Exception> callback) {
        new Thread(() -> {
            try {
                // Argon2 KDF
                Bencrypt b = new Bencrypt();
                this.Salt = b.Random(32);
                byte[] pwb = Bencode.NormPW(pw);
                Bencrypt.HashMaster hm = new Bencrypt.HashMaster(METHOD_HASH);
                byte[][] res = hm.KDF(pwb, this.Salt);
                sclear(pwb);
                this.PwHash = res[0];

                // Check if we should rotate the master key
                boolean mkRotate = !pw.equals(BASIC_PW);
                if (mkRotate) {
                    for (int i = 0; i < 8; i++) {
                        if (this.Names[i] == null) this.LoadNames(i);
                        if (this.Names[i].size() > 1) { // more than just folder title ("")
                            mkRotate = false;
                            break;
                        }
                    }
                }

                if (mkRotate) {
                    // get new master key
                    byte[] mk = b.Random(64);
                    Bencrypt.SymMaster sm = new Bencrypt.SymMaster(METHOD_SYM_INTERNAL, res[1]);
                    sclear(res[1]);
                    this.EncMasterKey = sm.EnBin(mk);
                    this.masterKey = this.mask.XOR(mk);
                    sclear(mk);
                    
                    // save account and re-encrypt texts and names
                    this.SaveAccount();
                    this.SaveTexts();
                    for (int i = 0; i < 8; i++) this.SaveNames(i);
                    
                } else {
                    // update the emk with new KDF result
                    Bencrypt.SymMaster sm = new Bencrypt.SymMaster(METHOD_SYM_INTERNAL, res[1]);
                    sclear(res[1]);
                    byte[] mk = this.mask.XOR(this.masterKey);
                    this.EncMasterKey = sm.EnBin(mk);
                    sclear(mk);
                    this.SaveAccount();
                }

                // send success callback
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.accept(null));
                }
            } catch (Exception e) {
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.accept(e));
                }
            }
            ClearDummy();
        }).start();
    }

    // texts.bin field
    public HashMap<String, String> Texts = new HashMap<String, String>(); // title: content
    public void LoadTexts() throws Exception {
        IO1.VFile f = IO1.GetLocal(this.context, "texts.bin");
        if (!f.Exists(this.context)) {
            this.Texts.clear();
            return;
        }

        Opsec ops = new Opsec();
        byte[] mk = this.mask.XOR(this.masterKey);
        byte[] head;
        try (java.io.InputStream is = f.OpenReader(this.context)) {
            // read opsec header
            head = ops.Read(is, -1);
            if (head.length == 0) throw new Exception("invalid opsec header");
            ops.View(head);
            ops.Decpw(mk, null);

            // read body
            byte[] encBody = is.readNBytes((int)ops.BodySize);
            Bencrypt.SymMaster sm = new Bencrypt.SymMaster(ops.BodyAlgo, ops.BodyKey);
            sclear(ops.BodyKey);
            byte[] payload = sm.DeBin(encBody);

            // decode texts
            this.Texts.clear();
            java.util.Map<String, byte[]> data = ops.DecodeCfg(payload);
            sclear(payload);
            for (String key : data.keySet()) this.Texts.put(key, new String(data.get(key), StandardCharsets.UTF_8));
            for (byte[] plainBuf : data.values()) {
                if (plainBuf != null) sclear(plainBuf);
            }
        } finally {
            sclear(mk);
        }
        ClearDummy();
    }
    public void SaveTexts() throws Exception {
        // prepare workers
        Bencrypt.SymMaster sm = new Bencrypt.SymMaster(METHOD_SYM_INTERNAL, new byte[44]);
        Opsec ops = new Opsec();
        HashMap<String, byte[]> temp = new HashMap<>();
        for (String key : this.Texts.keySet()) temp.put(key, this.Texts.get(key).getBytes(StandardCharsets.UTF_8));
        byte[] payload = ops.EncodeCfg(temp);
        for (byte[] plainBuf : temp.values()) {
            if (plainBuf != null) sclear(plainBuf);
        }

        // make opsec header
        ops.BodySize = sm.AfterSize(payload.length);
        ops.BodyAlgo = METHOD_SYM_INTERNAL;
        byte[] mk = this.mask.XOR(this.masterKey);
        byte[] head = ops.Encpw(METHOD_HASH_INTERNAL, mk, null);
        sclear(mk);

        // encrypt body
        sm = new Bencrypt.SymMaster(METHOD_SYM_INTERNAL, ops.BodyKey);
        sclear(ops.BodyKey);
        byte[] encBody = sm.EnBin(payload);
        sclear(payload);

        // write to file with padding
        IO1.VFile f = IO1.GetLocal(this.context, "texts.bin");
        try (java.io.OutputStream os = f.OpenWriter(this.context, false)) {
            ops.Write(os, head);
            os.write(encBody);
            Opsec.PadFile(os, Opsec.PadLen(head.length + encBody.length + 6)); // 6 is YAS2 header overhead
        }
        ClearDummy();
    }
    public String[] GetTextsTitles() {
        String[] titles = this.Texts.keySet().toArray(new String[0]);
        java.util.Arrays.sort(titles);
        return titles;
    }
    public String GetTextsContent(String title) {
        return this.Texts.get(title);
    }
    public void SetTextsContent(String oldTitle, String newTitle, String content) throws Exception {
        if (this.Texts.containsKey(newTitle) && !oldTitle.equals(newTitle)) throw new Exception("cannot overwrite existing note");
        this.Texts.remove(oldTitle);
        this.Texts.put(newTitle, content);
    }

    // names.bin field
    public HashMap<String, String>[] Names = new HashMap[8]; // dir 0-7, empty: dirname, plain: hexname
    public void LoadNames(int pos) throws Exception {
        // check existance
        IO1.VFile f = IO1.GetLocal(this.context, pos + "/names.bin");
        if (!f.Exists(this.context)) {
            this.Names[pos] = new HashMap<String, String>();
            this.Names[pos].put("", "folder" + pos);
            return;
        }

        // get opsec header
        Opsec ops = new Opsec();
        byte[] mk = this.mask.XOR(this.masterKey);
        byte[] head;
        byte[] payload;
        try (java.io.InputStream is = f.OpenReader(this.context)) {
            // read opsec header
            head = ops.Read(is, -1);
            if (head.length == 0) throw new Exception("invalid opsec header");
            ops.View(head);
            ops.Decpw(mk, null);

            // read body
            byte[] encBody = is.readNBytes((int)ops.BodySize);
            Bencrypt.SymMaster sm = new Bencrypt.SymMaster(ops.BodyAlgo, ops.BodyKey);
            sclear(ops.BodyKey);
            payload = sm.DeBin(encBody);
        } finally {
            sclear(mk);
        }

        // restore name map
        this.Names[pos] = new HashMap<String, String>();
        if (payload != null && payload.length > 0) {
            java.util.Map<String, byte[]> decoded = ops.DecodeCfg(payload);
            sclear(payload);
            for (String key : decoded.keySet()) this.Names[pos].put(key, new String(decoded.get(key), StandardCharsets.UTF_8));
            for (byte[] plainBuf : decoded.values()) {
                if (plainBuf != null) sclear(plainBuf);
            }
        }

        // add folder title
        if (!this.Names[pos].containsKey("")) {
            this.Names[pos].put("", "folder" + pos);
        }
        ClearDummy();
    }
    public void SaveNames(int pos) throws Exception {
        // check folder title
        if (this.Names[pos] == null) {
            this.Names[pos] = new HashMap<String, String>();
        }
        if (!this.Names[pos].containsKey("")) {
            this.Names[pos].put("", "folder" + pos);
        }

        // encode name map
        HashMap<String, byte[]> toEncode = new HashMap<>();
        for (String key : this.Names[pos].keySet()) toEncode.put(key, this.Names[pos].get(key).getBytes(StandardCharsets.UTF_8));
        Opsec ops = new Opsec();
        byte[] payload = ops.EncodeCfg(toEncode);
        for (byte[] plainBuf : toEncode.values()) {
            if (plainBuf != null) sclear(plainBuf);
        }

        // get opsec header
        Bencrypt.SymMaster sm = new Bencrypt.SymMaster(METHOD_SYM_INTERNAL, new byte[44]);
        ops.BodySize = sm.AfterSize(payload.length);
        ops.BodyAlgo = METHOD_SYM_INTERNAL;
        byte[] mk = this.mask.XOR(this.masterKey);
        byte[] head = ops.Encpw(METHOD_HASH_INTERNAL, mk, null);
        sclear(mk);

        // encrypt body
        sm = new Bencrypt.SymMaster(METHOD_SYM_INTERNAL, ops.BodyKey);
        sclear(ops.BodyKey);
        byte[] encBody = sm.EnBin(payload);
        sclear(payload);

        // ensure directory exists
        IO1.VFile root = IO1.GetLocal(this.context, "");
        root.CreateDir(this.context, String.valueOf(pos));

        // write num/names.bin
        IO1.VFile f = IO1.GetLocal(this.context, pos + "/names.bin");
        try (java.io.OutputStream os = f.OpenWriter(this.context, false)) {
            ops.Write(os, head);
            os.write(encBody);
            Opsec.PadFile(os, Opsec.PadLen(head.length + encBody.length + 6)); // 6 is YAS2 header overhead
        }
        ClearDummy();
    }
    public String[] GetDirNames() {
        String[] res = new String[8];
        for (int i = 0; i < 8; i++) {
            try {
                if (this.Names[i] == null) this.LoadNames(i);
                res[i] = this.Names[i].get("");
            } catch (Exception e) {
                res[i] = "folder" + i;
            }
        }
        return res;
    }
    public String[] GetFileNames(int pos) {
        if (this.Names[pos] == null) {
            try { this.LoadNames(pos); } catch (Exception e) { return new String[0]; }
        }
        java.util.ArrayList<String> list = new java.util.ArrayList<>();
        for (String key : this.Names[pos].keySet()) {
            if (!key.isEmpty()) list.add(key);
        }
        String[] res = list.toArray(new String[0]);
        java.util.Arrays.sort(res);
        return res;
    }
    public void RenameFile(int pos, String oldName, String newName) throws Exception {
        if (this.Names[pos] == null) this.LoadNames(pos);
        if (!this.Names[pos].containsKey(oldName)) throw new Exception("file not found");
        if (this.Names[pos].containsKey(newName)) throw new Exception("new name already exists");
        String hexName = this.Names[pos].remove(oldName);
        this.Names[pos].put(newName, hexName);
        this.SaveNames(pos);
    }
    public void ImportFile(int pos, IO1.VFile src, Consumer<Exception> callback) {
        new Thread(() -> {
            IO1.VFile dst = null;
            try {
                if (this.Names[pos] == null) this.LoadNames(pos);
                String originalName = src.GetName(this.context);

                // 1. Generate unique hex name
                Bencrypt b = new Bencrypt();
                String hexName;
                do {
                    hexName = Opsec.Crc32(b.Random(32)) + ".bin";
                } while (this.Names[pos].containsValue(hexName));

                // 2. Prepare encryption
                Opsec ops = new Opsec();
                ops.BodyAlgo = METHOD_SYM;
                long srcSize = src.GetSize(this.context);
                Bencrypt.SymMaster smSize = new Bencrypt.SymMaster(METHOD_SYM, new byte[44]);
                ops.BodySize = smSize.AfterSize(srcSize);

                byte[] mk = this.mask.XOR(this.masterKey);
                byte[] head = ops.Encpw(METHOD_HASH_INTERNAL, mk, null);
                sclear(mk);

                // 3. Encrypt and Save
                IO1.VFile root = IO1.GetLocal(this.context, "");
                root.CreateDir(this.context, String.valueOf(pos));

                dst = IO1.GetLocal(this.context, pos + "/" + hexName);
                try (java.io.InputStream is = src.OpenReader(this.context);
                     java.io.OutputStream os = dst.OpenWriter(this.context, false)) {
                    ops.Write(os, head);

                    Bencrypt.SymMaster smBody = new Bencrypt.SymMaster(METHOD_SYM, ops.BodyKey);
                    sclear(ops.BodyKey);
                    smBody.EnFile(is, srcSize, os);

                    Opsec.PadFile(os, Opsec.PadLen(head.length + ops.BodySize + 6));
                }

                // 4. Update Name Map
                if (this.Names[pos].containsKey(originalName)) {
                    String oldHexName = this.Names[pos].get(originalName);
                    if (oldHexName != null) {
                        IO1.VFile oldFile = IO1.GetLocal(this.context, pos + "/" + oldHexName);
                        if (oldFile.Exists(this.context)) {
                            oldFile.Delete(this.context);
                        }
                    }
                }
                this.Names[pos].put(originalName, hexName);
                this.SaveNames(pos);

                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.accept(null));
                }
            } catch (Exception e) {
                if (dst != null && dst.Exists(this.context)) {
                    dst.Delete(this.context);
                }
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.accept(e));
                }
            }
            ClearDummy();
        }).start();
    }
    public void ExportFile(int pos, String filename, Consumer<Exception> callback) {
        new Thread(() -> {
            try {
                // 1. Get physical name
                if (this.Names[pos] == null) this.LoadNames(pos);
                String hexName = this.Names[pos].get(filename);
                if (hexName == null) throw new Exception("file not found in notebook");

                // 2. Get src and dst
                IO1.VFile src = IO1.GetLocal(this.context, pos + "/" + hexName);
                if (!src.Exists(this.context)) throw new Exception("encrypted file missing");
                IO1.VFile dst = IO1.CreateDownloadsFile(this.context, filename);
                if (dst == null) throw new Exception("failed to create export file");

                Opsec ops = new Opsec();
                byte[] mk = this.mask.XOR(this.masterKey);
                try (java.io.InputStream is = src.OpenReader(this.context);
                     java.io.OutputStream os = dst.OpenWriter(this.context, false)) {
                    
                    // 1. Read and decrypt opsec header
                    byte[] head = ops.Read(is, -1);
                    if (head.length == 0) throw new Exception("invalid opsec header");
                    ops.View(head);
                    ops.Decpw(mk, null);

                    // 2. Decrypt body to download folder
                    Bencrypt.SymMaster sm = new Bencrypt.SymMaster(ops.BodyAlgo, ops.BodyKey);
                    sclear(ops.BodyKey);
                    sm.DeFile(is, ops.BodySize, os);
                } finally {
                    sclear(mk);
                }

                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.accept(null));
                }
            } catch (Exception e) {
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.accept(e));
                }
            }
            ClearDummy();
        }).start();
    }
    public byte[] ReadFile(int pos, String filename, long szLimit) throws Exception {
        // 1. Get physical name
        if (this.Names[pos] == null) this.LoadNames(pos);
        String hexName = this.Names[pos].get(filename);
        if (hexName == null) throw new Exception("file not found in notebook");

        // 2. Access encrypted file
        IO1.VFile src = IO1.GetLocal(this.context, pos + "/" + hexName);
        if (!src.Exists(this.context)) throw new Exception("encrypted file missing");

        Opsec ops = new Opsec();
        byte[] mk = this.mask.XOR(this.masterKey);
        try (java.io.InputStream is = src.OpenReader(this.context);
             java.io.ByteArrayOutputStream os = new java.io.ByteArrayOutputStream()) {
            
            // 3. Read and decrypt opsec header
            byte[] head = ops.Read(is, -1);
            if (head.length == 0) throw new Exception("invalid opsec header");
            ops.View(head);
            ops.Decpw(mk, null);

            // 4. Check size limit (using ciphertext body size)
            if (ops.BodySize > szLimit) throw new Exception("file size exceeds limit");

            // 5. Decrypt body to memory
            Bencrypt.SymMaster sm = new Bencrypt.SymMaster(ops.BodyAlgo, ops.BodyKey);
            sclear(ops.BodyKey);
            sm.DeFile(is, ops.BodySize, os);
            return os.toByteArray();
        } finally {
            sclear(mk);
            ClearDummy();
        }
    }
}
