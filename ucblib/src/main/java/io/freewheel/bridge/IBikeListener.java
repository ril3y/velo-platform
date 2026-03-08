/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package io.freewheel.bridge;
public interface IBikeListener extends android.os.IInterface
{
  /** Default implementation for IBikeListener. */
  public static class Default implements io.freewheel.bridge.IBikeListener
  {
    // Sensor data from UCB (called at ~1Hz during WORKOUT state)
    @Override public void onSensorData(int resistance, int rpm, int tilt, float power, long crankRevCount, int crankEventTime) throws android.os.RemoteException
    {
    }
    // Firmware state changes
    @Override public void onFirmwareStateChanged(int state, java.lang.String stateName) throws android.os.RemoteException
    {
    }
    // Connection state changes
    @Override public void onConnectionChanged(boolean connected, java.lang.String message) throws android.os.RemoteException
    {
    }
    // Workout state changes (started/stopped, possibly by watchdog)
    @Override public void onWorkoutStateChanged(boolean active, java.lang.String reason) throws android.os.RemoteException
    {
    }
    // Heart rate from BLE HRM
    @Override public void onHeartRate(int bpm, java.lang.String deviceName) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements io.freewheel.bridge.IBikeListener
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an io.freewheel.bridge.IBikeListener interface,
     * generating a proxy if needed.
     */
    public static io.freewheel.bridge.IBikeListener asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof io.freewheel.bridge.IBikeListener))) {
        return ((io.freewheel.bridge.IBikeListener)iin);
      }
      return new io.freewheel.bridge.IBikeListener.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
        data.enforceInterface(descriptor);
      }
      switch (code)
      {
        case INTERFACE_TRANSACTION:
        {
          reply.writeString(descriptor);
          return true;
        }
      }
      switch (code)
      {
        case TRANSACTION_onSensorData:
        {
          int _arg0;
          _arg0 = data.readInt();
          int _arg1;
          _arg1 = data.readInt();
          int _arg2;
          _arg2 = data.readInt();
          float _arg3;
          _arg3 = data.readFloat();
          long _arg4;
          _arg4 = data.readLong();
          int _arg5;
          _arg5 = data.readInt();
          this.onSensorData(_arg0, _arg1, _arg2, _arg3, _arg4, _arg5);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_onFirmwareStateChanged:
        {
          int _arg0;
          _arg0 = data.readInt();
          java.lang.String _arg1;
          _arg1 = data.readString();
          this.onFirmwareStateChanged(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_onConnectionChanged:
        {
          boolean _arg0;
          _arg0 = (0!=data.readInt());
          java.lang.String _arg1;
          _arg1 = data.readString();
          this.onConnectionChanged(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_onWorkoutStateChanged:
        {
          boolean _arg0;
          _arg0 = (0!=data.readInt());
          java.lang.String _arg1;
          _arg1 = data.readString();
          this.onWorkoutStateChanged(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_onHeartRate:
        {
          int _arg0;
          _arg0 = data.readInt();
          java.lang.String _arg1;
          _arg1 = data.readString();
          this.onHeartRate(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements io.freewheel.bridge.IBikeListener
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      // Sensor data from UCB (called at ~1Hz during WORKOUT state)
      @Override public void onSensorData(int resistance, int rpm, int tilt, float power, long crankRevCount, int crankEventTime) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(resistance);
          _data.writeInt(rpm);
          _data.writeInt(tilt);
          _data.writeFloat(power);
          _data.writeLong(crankRevCount);
          _data.writeInt(crankEventTime);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onSensorData, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      // Firmware state changes
      @Override public void onFirmwareStateChanged(int state, java.lang.String stateName) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(state);
          _data.writeString(stateName);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onFirmwareStateChanged, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      // Connection state changes
      @Override public void onConnectionChanged(boolean connected, java.lang.String message) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(((connected)?(1):(0)));
          _data.writeString(message);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onConnectionChanged, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      // Workout state changes (started/stopped, possibly by watchdog)
      @Override public void onWorkoutStateChanged(boolean active, java.lang.String reason) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(((active)?(1):(0)));
          _data.writeString(reason);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onWorkoutStateChanged, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      // Heart rate from BLE HRM
      @Override public void onHeartRate(int bpm, java.lang.String deviceName) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(bpm);
          _data.writeString(deviceName);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onHeartRate, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_onSensorData = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_onFirmwareStateChanged = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_onConnectionChanged = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_onWorkoutStateChanged = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
    static final int TRANSACTION_onHeartRate = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
  }
  public static final java.lang.String DESCRIPTOR = "io.freewheel.bridge.IBikeListener";
  // Sensor data from UCB (called at ~1Hz during WORKOUT state)
  public void onSensorData(int resistance, int rpm, int tilt, float power, long crankRevCount, int crankEventTime) throws android.os.RemoteException;
  // Firmware state changes
  public void onFirmwareStateChanged(int state, java.lang.String stateName) throws android.os.RemoteException;
  // Connection state changes
  public void onConnectionChanged(boolean connected, java.lang.String message) throws android.os.RemoteException;
  // Workout state changes (started/stopped, possibly by watchdog)
  public void onWorkoutStateChanged(boolean active, java.lang.String reason) throws android.os.RemoteException;
  // Heart rate from BLE HRM
  public void onHeartRate(int bpm, java.lang.String deviceName) throws android.os.RemoteException;
}
