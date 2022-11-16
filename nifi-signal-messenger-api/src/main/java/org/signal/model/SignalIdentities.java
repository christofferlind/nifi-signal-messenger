package org.signal.model;

import com.google.gson.annotations.SerializedName;

public class SignalIdentities {

	@SerializedName("number")
	private String number;

	@SerializedName("uuid")
	private String uuid;

	@SerializedName("fingerprint")
	private String fingerprint;

	@SerializedName("saftyNumber")
	private String saftyNumber;

	@SerializedName("scannableSaftyNumber")
	private String saftyNumberScannable;

	@SerializedName("trustLevel")
	private String trustLevel;

	@SerializedName("addedTimestamp")
	private long timestampAdded;

	public String getNumber() {
		return number;
	}

	public String getUuid() {
		return uuid;
	}

	public String getFingerprint() {
		return fingerprint;
	}

	public String getSaftyNumber() {
		return saftyNumber;
	}

	public String getSaftyNumberScannable() {
		return saftyNumberScannable;
	}

	public String getTrustLevel() {
		return trustLevel;
	}

	public long getTimestampAdded() {
		return timestampAdded;
	}
}
 