package org.signal.model;

import com.google.gson.annotations.SerializedName;

public class SignalGroup {
	@SerializedName("id")
	private String id;

	@SerializedName("name")
	private String name;

	@SerializedName("description")
	private String description;

	@SerializedName("messageExpirationTime")
	private long expirationTime = -1;

	@SerializedName("permissionAddMember")
	private String permissionAddMember;
	
	@SerializedName("permissionEditDetails")
	private String permissionEditDetails;

	@SerializedName("permissionSendMessage")
	private String permissionSendMessage;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public long getExpirationTime() {
		return expirationTime;
	}

	public void setExpirationTime(long expirationTime) {
		this.expirationTime = expirationTime;
	}

	public String getPermissionAddMember() {
		return permissionAddMember;
	}

	public void setPermissionAddMember(String permissionAddMember) {
		this.permissionAddMember = permissionAddMember;
	}

	public String getPermissionEditDetails() {
		return permissionEditDetails;
	}

	public void setPermissionEditDetails(String permissionEditDetails) {
		this.permissionEditDetails = permissionEditDetails;
	}

	public String getPermissionSendMessage() {
		return permissionSendMessage;
	}

	public void setPermissionSendMessage(String permissionSendMessage) {
		this.permissionSendMessage = permissionSendMessage;
	}
}
