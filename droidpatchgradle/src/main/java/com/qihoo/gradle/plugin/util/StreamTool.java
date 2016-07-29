package com.qihoo.gradle.plugin.util;


import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class StreamTool {
	public static byte[] readStream(InputStream inStream){
		if(inStream==null){
			return null;
		}
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int len;
		try {
			while ((len = inStream.read(buffer)) != -1) {
				outStream.write(buffer, 0, len);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}finally{
			try {
				outStream.flush();
				outStream.close();
				inStream.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return outStream.toByteArray();
	}
	public static void readStream(InputStream inStream, OutputStream outStream){
		if(inStream==null||outStream==null){
			return;
		}
		byte[] buffer = new byte[1024];
		int len;
		try {
			while ((len = inStream.read(buffer)) != -1) {
				outStream.write(buffer, 0, len);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}finally{
			try {
				outStream.flush();
				outStream.close();
				inStream.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

}
