package com.nautilus.serial.android_serialport_api;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * JNI wrapper matching the native function names in libserial_portx-lib.so.
 * Package name must be com.nautilus.serial.android_serialport_api to match
 * Java_com_nautilus_serial_android_1serialport_1api_SerialPort_open/close
 */
public class SerialPort {
    private FileDescriptor mFd;
    private FileInputStream mFileInputStream;
    private FileOutputStream mFileOutputStream;

    static {
        System.loadLibrary("serial_portx-lib");
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
