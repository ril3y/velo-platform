package io.freewheel.bridge;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * JNI wrapper for serial port access.
 * Matches the native interface of libserial_portx-lib.so from nautiluslauncher.
 */
public class SerialPort {
    private FileDescriptor mFd;
    private FileInputStream mFileInputStream;
    private FileOutputStream mFileOutputStream;

    static {
        try {
            System.loadLibrary("serial_portx-lib");
        } catch (UnsatisfiedLinkError e) {
            try {
                System.loadLibrary("serial_port-lib");
            } catch (UnsatisfiedLinkError e2) {
                throw new RuntimeException("Cannot load serial port library", e2);
            }
        }
    }

    public SerialPort(File device, int baudrate, int flags) throws IOException {
        if (!device.canRead() || !device.canWrite()) {
            // Try chmod if we have system permissions
            try {
                Process p = Runtime.getRuntime().exec("chmod 666 " + device.getAbsolutePath());
                p.waitFor();
            } catch (Exception e) {
                // ignore
            }
        }

        mFd = open(device.getAbsolutePath(), baudrate, flags);
        if (mFd == null) {
            throw new IOException("Failed to open serial port: " + device.getAbsolutePath());
        }
        mFileInputStream = new FileInputStream(mFd);
        mFileOutputStream = new FileOutputStream(mFd);
    }

    public FileInputStream getInputStream() { return mFileInputStream; }
    public FileOutputStream getOutputStream() { return mFileOutputStream; }

    public void closePort() {
        try { if (mFileInputStream != null) mFileInputStream.close(); } catch (Exception e) {}
        try { if (mFileOutputStream != null) mFileOutputStream.close(); } catch (Exception e) {}
        close();
    }

    private static native FileDescriptor open(String path, int baudrate, int flags);
    public native void close();
}
