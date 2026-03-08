package com.nautilus.ucbsystemdata.android_serialport_api;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * JNI wrapper matching libserial_port-lib.so (used by UcbSerialPortThread).
 * Package must match: com.nautilus.ucbsystemdata.android_serialport_api
 */
public class SerialPort {
    private FileDescriptor mFd;
    private FileInputStream mFileInputStream;
    private FileOutputStream mFileOutputStream;

    static {
        System.loadLibrary("serial_port-lib");
    }

    public SerialPort(File device, int baudrate, int flags) throws IOException {
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
