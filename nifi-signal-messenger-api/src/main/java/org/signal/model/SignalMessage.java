package org.signal.model;

import com.google.gson.annotations.SerializedName;

public class SignalMessage extends SignalData {
	@SerializedName("message")
	private String message;
	
	@SerializedName("expiresInSeconds")
	private long expires = -1;
	
	@SerializedName("viewOnce")
	private boolean viewOnce = false;

	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
	public long getExpires() {
		return expires;
	}
	public void setExpires(long expires) {
		this.expires = expires;
	}
	public boolean isViewOnce() {
		return viewOnce;
	}
	public void setViewOnce(boolean viewOnce) {
		this.viewOnce = viewOnce;
	}

	@Override
	public String toString() {
		return String.format("Msg from %s to %s", getSourceNumber(), getAccount());
	}
	
}
