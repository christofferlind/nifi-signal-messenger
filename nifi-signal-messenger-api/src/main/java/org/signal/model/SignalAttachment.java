package org.signal.model;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class SignalAttachment {
	private String mimeType;
	private String filename;
	private String base64Content;
	
	public SignalAttachment(String mimeType, String filename, String base64Content) {
		this.mimeType = Objects.requireNonNull(mimeType);
		this.filename = Objects.requireNonNull(filename);
		this.base64Content = Objects.requireNonNull(base64Content);
	}
	
	public String getMineType() {
		return mimeType;
	}
	
	public String getFilename() {
		return filename;
	}
	
	public String getBase64Content() {
		return base64Content;
	}
	
	public String toAttachmentParam() {
		StringBuilder builder = new StringBuilder();
		builder.append("data:").append(mimeType);
		builder.append(";filename=").append(URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20"));
		builder.append(";charset=utf8");
		builder.append(";base64,").append(base64Content);
		return builder.toString();
	}
}
