package android.content;

import java.io.File;
/** Test-only Context fixture; never packaged in the application. */
public class Context {
    private final File files;
    public Context(File files){this.files=files;}
    public File getFilesDir(){return files;}
}
