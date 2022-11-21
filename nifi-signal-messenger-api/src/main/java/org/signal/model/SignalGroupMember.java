package org.signal.model;

import com.google.gson.annotations.SerializedName;

public class SignalGroupMember {
	@SerializedName("uuid")
	private String uuid;

	@SerializedName("number")
	private String number;
	
	public String getNumber() {
		return number;
	}
	
	public String getUuid() {
		return uuid;
	}
	
	@Override
	public String toString() {
		return getNumber();
	}
}
