package org.signal.model;

public class SignalMessage extends SignalData {
	private String message;
	
	private long expires = -1;
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
		return String.format("Msg from %s to %s", getSource(), getAccount());
	}
	
}
