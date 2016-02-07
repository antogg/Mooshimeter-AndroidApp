/*
 * Copyright (c) Mooshim Engineering LLC 2015.
 *
 * This file is part of Mooshimeter-AndroidApp.
 *
 * Foobar is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Foobar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with Mooshimeter-AndroidApp.  If not, see
 * <http://www.gnu.org/licenses/>.
 */

package com.mooshim.mooshimeter.common;


import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

import static java.util.UUID.fromString;

public abstract class MooshimeterDeviceBase extends PeripheralWrapper {
    ////////////////////////////////
    // Statics
    ////////////////////////////////
    public static final class SignificantDigits {
        public int high;
        public int n_digits;
    }

    public static final class mPreferenceKeys {
        public static final String
                AUTOCONNECT = "AUTOCONNECT";
    }

    private static final String TAG="MooshimeterDevice";
    /*
    mUUID stores the UUID values of all the Mooshimeter fields.
    Note that the OAD fields are only accessible when connected to the Mooshimeter in OAD mode
    and the METER_ fields are only accessible when connected in meter mode.
     */

    public static final class mServiceUUIDs {
        public final static UUID
        METER_SERVICE      = fromString("1BC5FFA0-0200-62AB-E411-F254E005DBD4"),
        OAD_SERVICE_UUID   = fromString("1BC5FFC0-0200-62AB-E411-F254E005DBD4");
    }

    public enum TEMP_UNITS {
        CELSIUS,
        FAHRENHEIT,
        KELVIN
    }

    // Display control settings
    public final boolean[] disp_ac         = new boolean[]{false,false};
    public final boolean[] disp_hex        = new boolean[]{false,false};
    public TEMP_UNITS      disp_temp_units = TEMP_UNITS.CELSIUS;
    public final boolean[] disp_range_auto = new boolean[]{true,true};
    public boolean         disp_rate_auto  = true;
    public boolean         disp_depth_auto = true;

    public final double[] offsets = new double[]{0,0,0};

    protected static void putInt24(ByteBuffer b, int arg) {
        // Puts the bottom 3 bytes of arg on to b
        ByteBuffer tmp = ByteBuffer.allocate(4);
        byte[] tb = new byte[3];
        tmp.putInt(arg);
        tmp.flip();
        tmp.get(tb);
        b.put( tb );
    }
    protected static int  getInt24(ByteBuffer b) {
        // Pulls out a 3 byte int, expands it to 4 bytes
        // Advances the buffer by 3 bytes
        byte[] tb = new byte[4];
        b.get(tb, 0, 3);                   // Grab 3 bytes of the input
        if(tb[2] < 0) {tb[3] = (byte)0xFF;}// Sign extend
        else          {tb[3] = (byte)0x00;}
        ByteBuffer tmp = ByteBuffer.wrap(tb);
        tmp.order(ByteOrder.LITTLE_ENDIAN);
        return tmp.getInt();
    }

    protected static ByteBuffer wrap(byte[] in) {
        // Generates a little endian byte buffer wrapping the byte[]
        ByteBuffer b = ByteBuffer.wrap(in);
        b.order(ByteOrder.LITTLE_ENDIAN);
        return b;
    }


    // Used so the inner classes have something to grab
    public MooshimeterDeviceBase mInstance;

    public int              mBuildTime;
    public boolean          mOADMode;
    public boolean          mInitialized = false;

    public MooshimeterDeviceBase(final BluetoothDevice device, final Context context) {
        // Initialize super
        super(device,context);

        mInstance = this;
    }

    public int discover() {
        int rval = super.discover();
        if(rval != 0) {
            return rval;
        }
        mInitialized = true;
        return rval;
    }

    public int disconnect() {
        mInitialized = false;
        return super.disconnect();
    }

    ////////////////////////////////
    // Persistent settings
    ////////////////////////////////

    private String getSharedPreferenceString() {
        return "mooshimeter-preference-"+getAddress();
    }

    private SharedPreferences getSharedPreferences() {
        return mContext.getSharedPreferences(getSharedPreferenceString(),Context.MODE_PRIVATE);
    }

    public boolean hasPreference(String key) {
        return getSharedPreferences().contains(key);
    }

    public boolean getPreference(String key) {
        return getSharedPreferences().getBoolean(key, false);
    }

    public void setPreference(String key, boolean val) {
        SharedPreferences sp = getSharedPreferences();
        SharedPreferences.Editor e = sp.edit();
        e.putBoolean(key,val);
        e.commit();
    }

    ////////////////////////////////
    // Convenience functions
    ////////////////////////////////

    public boolean isInOADMode() {
        // If we've already connected and discovered characteristics,
        // we can just see what's in the service dictionary.
        // If we haven't connected, revert to whatever the scan
        // hinted at.
        if(mServices.containsKey(mServiceUUIDs.METER_SERVICE)){
            mOADMode = false;
        }
        if(mServices.containsKey(mServiceUUIDs.OAD_SERVICE_UUID)) {
            mOADMode = true;
        }
        return mOADMode;
    }

    /**
     * Returns the number of ADC samples that constitute a sample buffer
     * @return number of samples
     */
    public abstract int getBufLen();

    /**
     * Downloads the complete sample buffer from the Mooshimeter.
     * This interaction spans many connection intervals, the exact length depends on the number of samples in the buffer
     * @param onReceived Called when the complete buffer has been downloaded
     */
    public abstract void getBuffer(final Runnable onReceived);

    /**
     * Stop the meter from sending samples.  Opposite of playSampleStream
     */
    public abstract void pauseStream();

    /**
     * Start streaming samples from the meter.  Samples will be streamed continuously until pauseStream is called
     * @param on_notify Called whenever a new sample is received
     */
    public abstract void playSampleStream(final NotifyCallback on_notify);

    public abstract boolean isStreaming();

    public static String formatReading(double val, MooshimeterDeviceBase.SignificantDigits digits) {
        //TODO: Unify prefix handling.  Right now assume that in the area handling the units the correct prefix
        // is being applied
        while(digits.high > 4) {
            digits.high -= 3;
            val /= 1000;
        }
        while(digits.high <=0) {
            digits.high += 3;
            val *= 1000;
        }

        // TODO: Prefixes for units.  This will fail for wrong values of digits
        boolean neg = val<0;
        int left  = digits.high;
        int right = digits.n_digits - digits.high;
        String formatstring = String.format("%s%%0%d.%df",neg?"":" ", left+right+(neg?1:0), right); // To live is to suffer
        String retval;
        try {
            retval = String.format(formatstring, val);
        } catch ( java.util.UnknownFormatConversionException e ) {
            // Something went wrong with the string formatting, provide a default and log the error
            Log.e(TAG, "BAD FORMAT STRING");
            Log.e(TAG, formatstring);
            retval = "%f";
        }
        //Truncate
        retval = retval.substring(0, Math.min(retval.length(), 8));
        return retval;
    }

    //////////////////////////////////////
    // Autoranging
    //////////////////////////////////////

    /**
     * Changes the measurement settings for a channel to expand or contract the measurement range
     * @param channel   The channel index (0 or 1)
     * @param expand    Expand (true) or contract (false) the range.
     * @param wrap      If at a maximum or minimum gain, whether to wrap over to the other side of the range
     */
    public abstract void bumpRange(int channel, boolean expand, boolean wrap);

    public abstract void applyAutorange();

    //////////////////////////////////////
    // Data conversion
    //////////////////////////////////////

    /**
     * Based on the ENOB and the measurement range for the given channel, determine which digits are
     * significant in the output.
     * @param channel The channel index (0 or 1)
     * @return  A SignificantDigits structure, "high" is the number of digits to the left of the decimal point and "digits" is the number of significant digits
     */

    public abstract SignificantDigits getSigDigits(final int channel);

    /**
     *
     * @param channel The channel index (0 or 1)
     * @return A string describing what the channel is measuring
     */

    public abstract String getDescriptor(final int channel);

    /**
     *
     * @param channel The channel index (0 or 1)
     * @return A string containing the units label for the channel
     */

    public abstract String getUnits(final int channel);

    /**
     *
     * @param channel The channel index (0 or 1)
     * @return        A String containing the input label of the channel (V, A, Omega or Internal)
     */

    public abstract String getInputLabel(final int channel);
}
