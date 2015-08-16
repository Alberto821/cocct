package sir.multiply.clash.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static spark.Spark.*;

import sir.barchable.clash.protocol.*;
import sir.multiply.clash.messagequeue.*;
import sir.multiply.clash.api.JsonTransformer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class API {
	private static final Logger log = LoggerFactory.getLogger(API.class);

	private MessageQueue messageQueue;
	private MessageFactory messageFactory;

	public API(MessageQueue messageQueue, MessageFactory messageFactory) {
		this.messageQueue = messageQueue;
		this.messageFactory = messageFactory;

		port(32080);

		get("/api/version", (req, res) -> {
			return "0.0.1";
		});

		get("/api/clan/:id", (req, res) -> {
			final CountDownLatch latch = new CountDownLatch(1);
			Map<String, Object> result = new LinkedHashMap<>();

			Message message = API.this.messageFactory.newMessage(Pdu.Type.AskForAllianceData);
			message.set("clanId", Long.valueOf(req.params(":id")));

			MessageQueueSend send = new MessageQueueSend(message);

			MessageQueueExpect expect = new MessageQueueExpect() {
				private Boolean complete = false;

				public Boolean isComplete() {
					return this.complete;
				}

				public Boolean process(Pdu pdu, Message message) {
					if (pdu.getType() != Pdu.Type.AllianceData) {
						return false;
					}

					Map<String, Object> clan = message.getFields();
					result.put("members", clan.remove("clanMembers"));
					result.put("clan", clan);

					this.complete = true;
					return this.isComplete();
				}
			};

			MessageQueueCallback callback = new MessageQueueCallback() {
				public void run(MessageQueueItem item) {
					log.info("Done getting data");
					latch.countDown();
				}
			};

			MessageQueueItem item = new MessageQueueItem(send, expect, callback);

			API.this.messageQueue.addItem(item);

			latch.await();

			return result;
		}, new JsonTransformer());
	}

	public void stopServer() {
		stop();
	}
}