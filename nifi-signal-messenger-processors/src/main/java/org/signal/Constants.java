package org.signal;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class Constants {
	
	public static final String ATTRIBUTE_RECEIPT = 								"signal.receipt";
	public static final String ATTRIBUTE_RECEIPT_DELIVERY = 					"signal.receipt.delivery";
	public static final String ATTRIBUTE_RECEIPT_READ = 						"signal.receipt.read";

	public static final String ATTRIBUTE_CALL_MESSAGE = 						"signal.call";

	public static final String ATTRIBUTE_MESSAGE = 								"signal.message";
	public static final String ATTRIBUTE_MESSAGE_VIEW_ONCE = 					"signal.message.viewonce";
	public static final String ATTRIBUTE_TIMESTAMP = 							"signal.timestamp";
	public static final String ATTRIBUTE_TIMESTAMP_STRING = 					"signal.timestamp.string";
	public static final String ATTRIBUTE_ACCOUNT_NUMBER = 						"signal.account.number";
	public static final String ATTRIBUTE_RECEIVING_NUMBER = 					"signal.receiving.number";
	public static final String ATTRIBUTE_SENDER_NUMBER = 						"signal.sender.number";
	public static final String ATTRIBUTE_SENDER_VERIFIED = 						"signal.sender.verified";
	public static final String ATTRIBUTE_SENDER_IDENTIFIED = 					"signal.sender.identified";

	public static final String ATTRIBUTE_SENDER_TYPING_STARTED = 				"signal.sender.typing.started";
	public static final String ATTRIBUTE_SENDER_TYPING_STOPPED = 				"signal.sender.typing.stopped";
	
	public static final String ATTRIBUTE_MESSAGE_REACTION_EMOJI = 				"signal.message.reaction.emoji";
	public static final String ATTRIBUTE_MESSAGE_REACTION_TARGET_AUTHOR = 		"signal.message.reaction.target.author";
	public static final String ATTRIBUTE_MESSAGE_REACTION_TARGET_TIMESTAMP = 	"signal.message.reaction.target.timestamp";

	public static final String ATTRIBUTE_MESSAGE_QUOTE_ID = 					"signal.message.quote.id";
	
	public static final String ATTRIBUTE_MESSAGE_GROUP_ID = 					"signal.message.group.id";
	public static final String ATTRIBUTE_MESSAGE_GROUP_TITLE = 					"signal.message.group.title";

	public static final String ATTRIBUTE_ERROR_MESSAGE = 						"signal.error.message";
	
	public static final String ATTRIBUTE_ERROR_MESSAGE_SEND =  					"signal.send.error.message";

	public static final String getAndWait(AtomicReference<String> refContent) throws InterruptedException {
		Instant maxWait = Instant.now().plus(5, ChronoUnit.SECONDS);
		while(!Thread.currentThread().isInterrupted()) {
			String result = refContent.get();
			if(result != null)
				return result;
			
			Thread.sleep(101);
			
			if(Instant.now().isAfter(maxWait))
				break;
		}
		
		return null;
	}

	static final String MSG_MISSING_RECIPIENT_AND_GROUP = "Neither groups nor recipients is specified";

	public static final List<String> getCommaSeparatedList(String string) {
		if(string == null)
			return Collections.emptyList();
		
		String[] split = string.split(",");
		List<String> recipients = new ArrayList<>(split.length);
		for (String element : split) {
			String trimmed = element.trim();
			if(trimmed.isEmpty())
				continue;
	
			recipients.add(trimmed);
		}
		return recipients;
	}
}
