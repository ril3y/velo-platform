/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package io.freewheel.bridge;
public interface IBikeService extends android.os.IInterface
{
  /** Default implementation for IBikeService. */
  public static class Default implements io.freewheel.bridge.IBikeService
  {
    // Binding - single client at a time
    // Returns true if successfully claimed, false if another client is active
    @Override public boolean claimSession(java.lang.String packageName) throws android.os.RemoteException
    {
      return false;
    }
    @Override public void releaseSession() throws android.os.RemoteException
    {
    }
    // Workout control (only works for session owner)
    @Override public boolean startWorkout() throws android.os.RemoteException
    {
      return false;
    }
    @Override public boolean stopWorkout() throws android.os.RemoteException
    {
      return false;
    }
    @Override public boolean setResistance(int level) throws android.os.RemoteException
    {
      return false;
    }
    // Heartbeat - client must call every 5s during active workout
    @Override public void heartbeat() throws android.os.RemoteException
    {
    }
    // State queries
    @Override public boolean isWorkoutActive() throws android.os.RemoteException
    {
      return false;
    }
    @Override public java.lang.String getSessionOwner() throws android.os.RemoteException
    {
      return null;
    }
    @Override public int getFirmwareState() throws android.os.RemoteException
    {
      return 0;
    }
    // Listener registration
    @Override public void registerListener(io.freewheel.bridge.IBikeListener listener) throws android.os.RemoteException
    {
    }
    @Override public void unregisterListener(io.freewheel.bridge.IBikeListener listener) throws android.os.RemoteException
    {
    }
    // Heart rate monitor
    @Override public int getHeartRate() throws android.os.RemoteException
    {
      return 0;
    }
    @Override public java.lang.String getConnectedHrmName() throws android.os.RemoteException
    {
      return null;
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements io.freewheel.bridge.IBikeService
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an io.freewheel.bridge.IBikeService interface,
     * generating a proxy if needed.
     */
    public static io.freewheel.bridge.IBikeService asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof io.freewheel.bridge.IBikeService))) {
        return ((io.freewheel.bridge.IBikeService)iin);
      }
      return new io.freewheel.bridge.IBikeService.Stub.Proxy(obj);
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
        case TRANSACTION_claimSession:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          boolean _result = this.claimSession(_arg0);
          reply.writeNoException();
          reply.writeInt(((_result)?(1):(0)));
          break;
        }
        case TRANSACTION_releaseSession:
        {
          this.releaseSession();
          reply.writeNoException();
          break;
        }
        case TRANSACTION_startWorkout:
        {
          boolean _result = this.startWorkout();
          reply.writeNoException();
          reply.writeInt(((_result)?(1):(0)));
          break;
        }
        case TRANSACTION_stopWorkout:
        {
          boolean _result = this.stopWorkout();
          reply.writeNoException();
          reply.writeInt(((_result)?(1):(0)));
          break;
        }
        case TRANSACTION_setResistance:
        {
          int _arg0;
          _arg0 = data.readInt();
          boolean _result = this.setResistance(_arg0);
          reply.writeNoException();
          reply.writeInt(((_result)?(1):(0)));
          break;
        }
        case TRANSACTION_heartbeat:
        {
          this.heartbeat();
          reply.writeNoException();
          break;
        }
        case TRANSACTION_isWorkoutActive:
        {
          boolean _result = this.isWorkoutActive();
          reply.writeNoException();
          reply.writeInt(((_result)?(1):(0)));
          break;
        }
        case TRANSACTION_getSessionOwner:
        {
          java.lang.String _result = this.getSessionOwner();
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_getFirmwareState:
        {
          int _result = this.getFirmwareState();
          reply.writeNoException();
          reply.writeInt(_result);
          break;
        }
        case TRANSACTION_registerListener:
        {
          io.freewheel.bridge.IBikeListener _arg0;
          _arg0 = io.freewheel.bridge.IBikeListener.Stub.asInterface(data.readStrongBinder());
          this.registerListener(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_unregisterListener:
        {
          io.freewheel.bridge.IBikeListener _arg0;
          _arg0 = io.freewheel.bridge.IBikeListener.Stub.asInterface(data.readStrongBinder());
          this.unregisterListener(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_getHeartRate:
        {
          int _result = this.getHeartRate();
          reply.writeNoException();
          reply.writeInt(_result);
          break;
        }
        case TRANSACTION_getConnectedHrmName:
        {
          java.lang.String _result = this.getConnectedHrmName();
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements io.freewheel.bridge.IBikeService
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
      // Binding - single client at a time
      // Returns true if successfully claimed, false if another client is active
      @Override public boolean claimSession(java.lang.String packageName) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(packageName);
          boolean _status = mRemote.transact(Stub.TRANSACTION_claimSession, _data, _reply, 0);
          _reply.readException();
          _result = (0!=_reply.readInt());
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public void releaseSession() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_releaseSession, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      // Workout control (only works for session owner)
      @Override public boolean startWorkout() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_startWorkout, _data, _reply, 0);
          _reply.readException();
          _result = (0!=_reply.readInt());
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public boolean stopWorkout() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_stopWorkout, _data, _reply, 0);
          _reply.readException();
          _result = (0!=_reply.readInt());
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public boolean setResistance(int level) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(level);
          boolean _status = mRemote.transact(Stub.TRANSACTION_setResistance, _data, _reply, 0);
          _reply.readException();
          _result = (0!=_reply.readInt());
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      // Heartbeat - client must call every 5s during active workout
      @Override public void heartbeat() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_heartbeat, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      // State queries
      @Override public boolean isWorkoutActive() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_isWorkoutActive, _data, _reply, 0);
          _reply.readException();
          _result = (0!=_reply.readInt());
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.lang.String getSessionOwner() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getSessionOwner, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public int getFirmwareState() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        int _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getFirmwareState, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readInt();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      // Listener registration
      @Override public void registerListener(io.freewheel.bridge.IBikeListener listener) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(listener);
          boolean _status = mRemote.transact(Stub.TRANSACTION_registerListener, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void unregisterListener(io.freewheel.bridge.IBikeListener listener) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(listener);
          boolean _status = mRemote.transact(Stub.TRANSACTION_unregisterListener, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      // Heart rate monitor
      @Override public int getHeartRate() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        int _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getHeartRate, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readInt();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.lang.String getConnectedHrmName() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getConnectedHrmName, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
    }
    static final int TRANSACTION_claimSession = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_releaseSession = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_startWorkout = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_stopWorkout = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
    static final int TRANSACTION_setResistance = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
    static final int TRANSACTION_heartbeat = (android.os.IBinder.FIRST_CALL_TRANSACTION + 5);
    static final int TRANSACTION_isWorkoutActive = (android.os.IBinder.FIRST_CALL_TRANSACTION + 6);
    static final int TRANSACTION_getSessionOwner = (android.os.IBinder.FIRST_CALL_TRANSACTION + 7);
    static final int TRANSACTION_getFirmwareState = (android.os.IBinder.FIRST_CALL_TRANSACTION + 8);
    static final int TRANSACTION_registerListener = (android.os.IBinder.FIRST_CALL_TRANSACTION + 9);
    static final int TRANSACTION_unregisterListener = (android.os.IBinder.FIRST_CALL_TRANSACTION + 10);
    static final int TRANSACTION_getHeartRate = (android.os.IBinder.FIRST_CALL_TRANSACTION + 11);
    static final int TRANSACTION_getConnectedHrmName = (android.os.IBinder.FIRST_CALL_TRANSACTION + 12);
  }
  public static final java.lang.String DESCRIPTOR = "io.freewheel.bridge.IBikeService";
  // Binding - single client at a time
  // Returns true if successfully claimed, false if another client is active
  public boolean claimSession(java.lang.String packageName) throws android.os.RemoteException;
  public void releaseSession() throws android.os.RemoteException;
  // Workout control (only works for session owner)
  public boolean startWorkout() throws android.os.RemoteException;
  public boolean stopWorkout() throws android.os.RemoteException;
  public boolean setResistance(int level) throws android.os.RemoteException;
  // Heartbeat - client must call every 5s during active workout
  public void heartbeat() throws android.os.RemoteException;
  // State queries
  public boolean isWorkoutActive() throws android.os.RemoteException;
  public java.lang.String getSessionOwner() throws android.os.RemoteException;
  public int getFirmwareState() throws android.os.RemoteException;
  // Listener registration
  public void registerListener(io.freewheel.bridge.IBikeListener listener) throws android.os.RemoteException;
  public void unregisterListener(io.freewheel.bridge.IBikeListener listener) throws android.os.RemoteException;
  // Heart rate monitor
  public int getHeartRate() throws android.os.RemoteException;
  public java.lang.String getConnectedHrmName() throws android.os.RemoteException;
}
