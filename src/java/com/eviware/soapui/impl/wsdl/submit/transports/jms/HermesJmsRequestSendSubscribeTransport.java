/*
 *  soapUI, copyright (C) 2004-2009 eviware.com 
 *
 *  soapUI is free software; you can redistribute it and/or modify it under the 
 *  terms of version 2.1 of the GNU Lesser General Public License as published by 
 *  the Free Software Foundation.
 *
 *  soapUI is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without 
 *  even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 *  See the GNU Lesser General Public License for more details at gnu.org.
 */

package com.eviware.soapui.impl.wsdl.submit.transports.jms;

import hermes.Domain;
import hermes.Hermes;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.model.iface.Request;
import com.eviware.soapui.model.iface.Response;
import com.eviware.soapui.model.iface.SubmitContext;
import com.eviware.soapui.model.propertyexpansion.PropertyExpander;

public class HermesJmsRequestSendSubscribeTransport extends HermesJmsRequestTransport
{

	public Response execute(SubmitContext submitContext, Request request, long timeStarted) throws Exception
	{
		ConnectionFactory connectionFactory = null;
		Connection connection = null;
		Session session = null;
		TopicSubscriber topicSubsriber = null;
		try
		{
			String queueNameSend = null;
			String topicNameReceive = null;
			String sessionName = null;
			String[] parameters = request.getEndpoint().substring(request.getEndpoint().indexOf("://") + 3).split("/");
			if (parameters.length == 3)
			{
				sessionName = PropertyExpander.expandProperties(submitContext, parameters[0]);
				queueNameSend = PropertyExpander.expandProperties(submitContext, parameters[1]).replaceFirst("queue_", "");
				topicNameReceive = PropertyExpander.expandProperties(submitContext, parameters[2]).replaceFirst("topic_",
						"");
			}
			else
				throw new Exception("bad jms alias!!!!!");

			submitContext.setProperty(HERMES_SESSION_NAME, sessionName);

			Hermes hermes = getHermes(sessionName, request);

			connectionFactory = (javax.jms.ConnectionFactory) hermes.getConnectionFactory();

			connection = connectionFactory.createConnection();
			connection.setClientID("" + (Math.random() * 1000));
			connection.start();

			session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

			Queue queueSend = (Queue) hermes.getDestination(queueNameSend, Domain.QUEUE);
			Topic topicReceive = (Topic) hermes.getDestination(topicNameReceive, Domain.TOPIC);

			topicSubsriber = session.createDurableSubscriber(topicReceive, "durableSubscription" + topicNameReceive);
			
			TextMessage textMessageSend = messageSend(submitContext, request, session, hermes, queueSend);

			return makeResponse(submitContext, request, timeStarted, textMessageSend, topicSubsriber);
		}
		catch (JMSException jmse)
		{
			return errorResponse(submitContext, request, timeStarted, jmse);
		}
		catch (Throwable t)
		{
			SoapUI.logError(t);
		}
		finally
		{
			if (topicSubsriber != null)
				topicSubsriber.close();
			closeSessionAndConnection(connection, session);
		}
		return null;
	}
}
