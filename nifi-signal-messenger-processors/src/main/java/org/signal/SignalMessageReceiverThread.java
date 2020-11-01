package org.signal;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;

public class SignalMessageReceiverThread extends Thread {
	private Consumer<Throwable> onError;
	private Consumer<SignalServiceEnvelope> messageHandler;
	private volatile boolean stopped = false;
	private SignalControllerService service;

	public SignalMessageReceiverThread(
			SignalControllerService service, 
			Consumer<SignalServiceEnvelope> messageHandler,
			Consumer<Throwable> onError) {
		super("Signal Message Receiver");
		Objects.requireNonNull(messageHandler, "messageHandler can not be null");
		Objects.requireNonNull(service);
		this.service = service;
		this.messageHandler = messageHandler;
		this.onError = onError;
	}

	@Override
	public void run() {

		while (!isInterrupted()) {
			try {
				service.getMessagePipe().readOrEmpty(1, TimeUnit.MINUTES, messageHandler::accept);
			} catch (TimeoutException e) {
				//Do nothing, just continue
			} catch (IOException e) {
				if("Connection closed!".equals(e.getMessage())) {
					return;
				}
			} catch (AssertionError e) {
				if(e.getCause() instanceof InterruptedException) {
					this.interrupt();
					return;
				}
			} catch (Throwable e) {
				if (e instanceof InterruptedException) {
					this.interrupt();
					return;
				}

				if (onError != null)
					onError.accept(e);
			} finally {
				service.saveAccount();
			}

			if(stopped)
				return;
		} 
	}

	@Override
	public void interrupt() {
		super.interrupt();
		stopped = true;
	}
}
