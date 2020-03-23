package org.signal;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.whispersystems.signalservice.api.SignalServiceMessagePipe;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;

public class SignalMessageReceiverThread extends Thread {
	private SignalServiceMessageReceiver messageReceiver;
	private Consumer<Throwable> onError;
	private Consumer<SignalServiceEnvelope> messageHandler;
	private Supplier<Boolean> waitUntil;
	private volatile SignalServiceMessagePipe pipe = null;
	private volatile boolean stopped = false;
	private SignalControllerService service;

	public SignalMessageReceiverThread(
			SignalControllerService service, 
			SignalServiceMessageReceiver messageReceiver, 
			Consumer<SignalServiceEnvelope> messageHandler) {
		
		Objects.requireNonNull(messageHandler, "messageHandler can not be null");
		Objects.requireNonNull(service);
		this.service = service;
		this.messageReceiver = messageReceiver;
		this.messageHandler = messageHandler;
	}
	
	public void setOnError(Consumer<Throwable> onError) {
		this.onError = onError;
	}

	public void setWaitUntil(Supplier<Boolean> waitUntil) {
		this.waitUntil = waitUntil;
	}

	@Override
	public void run() {
		try {
			if(waitUntil != null) {
				while (!waitUntil.get()) {
					Thread.sleep(39);
				}
			}
		} catch (InterruptedException e1) {
			interrupt();
			return;
		}

		pipe = messageReceiver.createMessagePipe();
		try {
			while (!isInterrupted()) {
				try {
					pipe.read(3600_000, TimeUnit.MILLISECONDS, messageHandler::accept);
					service.saveAccount();
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
				}
				
				if(stopped)
					return;
			} 
		} finally {
			try {
				stopPipe();
			} catch (Throwable e) {}
		}
	}

	@Override
	public void interrupt() {
		try {
			stopPipe();
		} catch (Throwable e) {}
		super.interrupt();
		stopped = true;
	}

	private void stopPipe() {
		if(pipe != null) {
			pipe.shutdown();
			pipe = null;
		}
	}
}
