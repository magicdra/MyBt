package com.cyberinco.btlibrary;

import java.util.List;

/**
 * hex工具类
 * 用于异或校验，字节与int数据的互相转换
 * @author syf
 */

class HexUtil {
  /**
   * 异或校验
   * @param datas 需要校验的数组
   * @return
   */
  public static byte getXor(byte[] datas) {

    byte temp = datas[0];

    for (int i = 1; i < datas.length - 1; i++) {
      temp ^= datas[i];
    }

    return temp;
  }

  /**
   * 两字节转int
   * @param bytes
   * @return
   */
  public static int twoBytesToInt(byte[] bytes) {
    int ints = bytes[0] & 0xFF;
    ints = ints << 8;
    ints += bytes[1] & 0xFF;
    return ints;
  }



  /**
   * int转两字节字节数组
   * @param data
   * @return
   */
  public static byte[] intToTwoBytes(int data) {
    byte[] bytes = new byte[2];
    bytes[1] = (byte) data;
    bytes[0] = (byte) (data >> 8);
    return bytes;
  }

  /**
   * Returns an int representation of the concatenation of the given array.
   *
   * @param array the array whose concatenation to icon_return.
   * @return an int representation of the concatenation.
   */
  public static int fourBytesToInt(byte[] array) {
    assert array != null;

    return (array[0] & 0xff) << 24 |
           (array[1] & 0xff) << 16 |
           (array[2] & 0xff) << 8 |
           (array[3] & 0xff);
  }

  /**
   * int 转四字节
   * @param data
   * @return
   */
  public static byte[] intToFourBytes(int data) {
    byte[] buffer = new byte[4];
    buffer[0] = (byte)(data >>> 24);
    buffer[1] = (byte)(data >>> 16);
    buffer[2] = (byte)(data >>> 8);
    buffer[3] = (byte)(data >>> 0);
    return buffer;
  }

  public static byte[] long2Bytes(long num) {
    byte[] byteNum = new byte[8];
    for (int ix = 0; ix < 8; ++ix) {
      int offset = 64 - (ix + 1) * 8;
      byteNum[ix] = (byte) ((num >> offset) & 0xff);
    }
    return byteNum;
  }

  public static long bytes2Long(byte[] byteNum) {
    long num = 0;
    for (int ix = 0; ix < 8; ++ix) {
      num <<= 8;
      num |= (byteNum[ix] & 0xff);
    }
    return num;
  }

  /**
   * @功能: BCD码转为10进制串(阿拉伯数据)
   * @参数: BCD码
   * @结果: 10进制串
   */
  public static String bcd2Str(byte[] bytes) {
    StringBuffer temp = new StringBuffer(bytes.length * 2);
    for (int i = 0; i < bytes.length; i++) {
      temp.append((byte) ((bytes[i] & 0xf0) >>> 4));
      temp.append((byte) (bytes[i] & 0x0f));
    }
    return temp.toString();
  }

  /**
   * @功能: 10进制串转为BCD码
   * @参数: 10进制串
   * @结果: BCD码
   */
  public static byte[] str2Bcd(String asc) {
    int len = asc.length();
    int mod = len % 2;
    if (mod != 0) {
      asc = "0" + asc;
      len = asc.length();
    }
    byte abt[] = new byte[len];
    if (len >= 2) {
      len = len / 2;
    }
    byte bbt[] = new byte[len];
    abt = asc.getBytes();
    int j, k;
    for (int p = 0; p < asc.length() / 2; p++) {
      if ((abt[2 * p] >= '0') && (abt[2 * p] <= '9')) {
        j = abt[2 * p] - '0';
      } else if ((abt[2 * p] >= 'a') && (abt[2 * p] <= 'z')) {
        j = abt[2 * p] - 'a' + 0x0a;
      } else {
        j = abt[2 * p] - 'A' + 0x0a;
      }
      if ((abt[2 * p + 1] >= '0') && (abt[2 * p + 1] <= '9')) {
        k = abt[2 * p + 1] - '0';
      } else if ((abt[2 * p + 1] >= 'a') && (abt[2 * p + 1] <= 'z')) {
        k = abt[2 * p + 1] - 'a' + 0x0a;
      } else {
        k = abt[2 * p + 1] - 'A' + 0x0a;
      }
      int a = (j << 4) + k;
      byte b = (byte) a;
      bbt[p] = b;
    }
    return bbt;
  }

  /**
   * 字符串转换为16进制字符串
   *
   * @param s
   * @return
   */
  public static String stringToHexString(String s) {
    String str = "";
    for (int i = 0; i < s.length(); i++) {
      int ch = (int) s.charAt(i);
      String s4 = Integer.toHexString(ch);
      str = str + s4;
    }
    return str;
  }

  /**
   * 多个byte[]合并成一个byte[]
   * @param srcArrays 要合并的byte[] list
   * @return
   */
  public static byte[] sysCopy(List<byte[]> srcArrays) {

    int len = 0;

    for(byte[] srcArray:srcArrays){

      len += srcArray.length;

    }

    byte[] destArray = new byte[len];

    int destLen = 0;

    for(byte[] srcArray:srcArrays){

      System.arraycopy(srcArray, 0, destArray, destLen, srcArray.length);

      destLen += srcArray.length;

    }
    return destArray;
  }

  public static double twoBcdBytestoDoubule(byte[] bytes){
    byte a = (byte) (bytes[0] << 4);
    byte b = (byte) ((a >> 4));
    byte c = (byte) (bytes[0] >> 4 & 0x0f);
    byte d = (byte) (bytes[1] << 4);
    byte e = (byte) (d >> 4 & 0x0f);
    byte f = (byte) (bytes[1] >> 4 & 0x0f);
    return (double)((b+c*10)*100 + (e + f*10))/100;
  }

  public static byte[] bytesMove(byte[] bytes, int move){
    for (int i = 0 ; i < bytes.length ; i++){
      bytes[i] = (byte) (bytes[i] >> move);
    }
    return bytes;
  }
}
