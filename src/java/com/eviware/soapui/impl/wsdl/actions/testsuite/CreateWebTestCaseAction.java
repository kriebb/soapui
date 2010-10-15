/*
 *  soapUI, copyright (C) 2004-2010 eviware.com 
 *
 *  soapUI is free software; you can redistribute it and/or modify it under the 
 *  terms of version 2.1 of the GNU Lesser General Public License as published by 
 *  the Free Software Foundation.
 *
 *  soapUI is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without 
 *  even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 *  See the GNU Lesser General Public License for more details at gnu.org.
 */
package com.eviware.soapui.impl.wsdl.actions.testsuite;

import org.apache.log4j.Logger;

import com.eviware.soapui.config.HttpRequestConfig;
import com.eviware.soapui.config.TestStepConfig;
import com.eviware.soapui.impl.rest.panels.request.views.html.HttpHtmlResponseView;
import com.eviware.soapui.impl.support.HttpUtils;
import com.eviware.soapui.impl.wsdl.WsdlTestSuite;
import com.eviware.soapui.impl.wsdl.panels.teststeps.HttpTestRequestDesktopPanel;
import com.eviware.soapui.impl.wsdl.support.HelpUrls;
import com.eviware.soapui.impl.wsdl.testcase.WsdlTestCase;
import com.eviware.soapui.impl.wsdl.testcase.WsdlTestRunContext;
import com.eviware.soapui.impl.wsdl.teststeps.HttpTestRequest;
import com.eviware.soapui.impl.wsdl.teststeps.HttpTestRequestStep;
import com.eviware.soapui.impl.wsdl.teststeps.registry.HttpRequestStepFactory;
import com.eviware.soapui.model.iface.Submit;
import com.eviware.soapui.model.iface.SubmitContext;
import com.eviware.soapui.model.iface.SubmitListener;
import com.eviware.soapui.model.iface.Request.SubmitException;
import com.eviware.soapui.model.iface.Submit.Status;
import com.eviware.soapui.support.MessageSupport;
import com.eviware.soapui.support.StringUtils;
import com.eviware.soapui.support.UISupport;
import com.eviware.soapui.support.action.support.AbstractSoapUIAction;
import com.eviware.x.form.XFormDialog;
import com.eviware.x.form.support.ADialogBuilder;
import com.eviware.x.form.support.AField;
import com.eviware.x.form.support.AForm;
import com.eviware.x.form.support.AField.AFieldType;
import com.sun.java.xml.ns.j2Ee.HttpMethodType;

public class CreateWebTestCaseAction extends AbstractSoapUIAction<WsdlTestSuite>
{

	public static final String SOAPUI_ACTION_ID = "CreateWebTestCaseAction";
	private WsdlTestSuite testSuite;
	public static final MessageSupport messages = MessageSupport.getMessages( CreateWebTestCaseAction.class );
	private XFormDialog dialog;
	HttpTestRequestDesktopPanel desktopPanel;
	private final static Logger logger = Logger.getLogger( CreateWebTestCaseAction.class );

	public CreateWebTestCaseAction()
	{
		super( "New Web TestCase", "Creates a new Web TestCase in this TestSuite" );
	}

	public void perform( WsdlTestSuite target, Object param )
	{
		this.testSuite = target;
		if( dialog == null )
		{
			dialog = ADialogBuilder.buildDialog( Form.class );
		}

		dialog.setValue( Form.TESTCASENAME, "Web TestCase" );
		dialog.setValue( Form.URL, "" );
		dialog.setValue( Form.STARTRECORDING, Boolean.toString( true ) );
		if( dialog.show() )
		{
			String targetTestCaseName = dialog.getValue( Form.TESTCASENAME );
			while( StringUtils.isNullOrEmpty( dialog.getValue( Form.URL ) ) )
			{
				UISupport.showErrorMessage( "You must specify the web address to start at" );
				dialog.show();
			}
			String url = dialog.getValue( Form.URL );
			String testStepName = HttpUtils.ensureEndpointStartsWithProtocol( url );
			WsdlTestCase targetTestCase = null;

			targetTestCase = testSuite.getTestCaseByName( targetTestCaseName );
			if( targetTestCase == null )
			{
				while( testSuite.getTestCaseByName( targetTestCaseName ) != null )
				{
					targetTestCaseName = UISupport.prompt(
							"TestCase name must be unique, please specify new name for TestCase\n" + "[" + targetTestCaseName
									+ "] in TestSuite [" + testSuite.getName() + "->" + testSuite.getName() + "]",
							"Change TestCase name", targetTestCaseName );
					if( targetTestCaseName == null )
						return;
				}
				targetTestCase = testSuite.addNewTestCase( targetTestCaseName );

			}
			while( testStepName == null || targetTestCase.getTestStepByName( testStepName ) != null )
			{
				testStepName = UISupport.prompt( "TestStep name must be unique, please specify new name for step\n" + "["
						+ testStepName + "] in TestCase [" + testSuite.getName() + "->" + testSuite.getName() + "->"
						+ targetTestCaseName + "]", "Change TestStep name", testStepName );

				if( testStepName == null )
					return;
			}
			createWebTest( targetTestCase, HttpUtils.ensureEndpointStartsWithProtocol( url ), testStepName, dialog
					.getBooleanValue( Form.STARTRECORDING ) );

		}
	}

	public void createWebTest( WsdlTestCase targetTestCase, String endpoint, String name, final boolean startRecording )
	{
		HttpRequestConfig httpRequest = HttpRequestConfig.Factory.newInstance();
		httpRequest.setMethod( HttpMethodType.GET.toString() );

		httpRequest.setEndpoint( endpoint );

		TestStepConfig testStepConfig = TestStepConfig.Factory.newInstance();
		testStepConfig.setType( HttpRequestStepFactory.HTTPREQUEST_TYPE );
		testStepConfig.setConfig( httpRequest );
		testStepConfig.setName( name );
		HttpTestRequestStep testStep = ( HttpTestRequestStep )targetTestCase.addTestStep( testStepConfig );

		desktopPanel = ( HttpTestRequestDesktopPanel )UISupport.selectAndShow( testStep );
		HttpTestRequest testRequest = null;
		WebTestSubmitListener wtListener = new WebTestSubmitListener( startRecording );
		try
		{
			testRequest = testStep.getTestRequest();
			testRequest.addSubmitListener( wtListener );
			testRequest.submit( new WsdlTestRunContext( testStep ), true );
		}
		catch( SubmitException e )
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// if( testRequest != null )
		// {
		// testRequest.removeSubmitListener( wtListener );
		// }
		desktopPanel.focusResponseInTabbedView( true );
	}

	private final class WebTestSubmitListener implements SubmitListener
	{
		private final boolean startRecording;

		private WebTestSubmitListener( boolean startRecording )
		{
			this.startRecording = startRecording;
		}

		@Override
		public boolean beforeSubmit( Submit submit, SubmitContext context )
		{
			return true;
		}

		@Override
		public void afterSubmit( Submit submit, SubmitContext context )
		{
			Status status = submit.getStatus();
			logger.debug( "submit status: " + status );
			if( status == Status.FINISHED )
			{
				if( startRecording )
				{
					HttpHtmlResponseView htmlResponseView = ( HttpHtmlResponseView )desktopPanel.getResponseEditor()
							.getViews().get( 2 );
					htmlResponseView.setRecordHttpTrafic( true );
				}
			}
		}
	}

	@AForm( description = "Specify Web TestCase Options", name = "Add Web TestCase", helpUrl = HelpUrls.CLONETESTSUITE_HELP_URL, icon = UISupport.TOOL_ICON_PATH )
	public interface Form
	{
		@AField( name = "Web Address", description = "The web address to start at", type = AField.AFieldType.STRING )
		public final static String URL = "Web Address";

		@AField( name = "Web TestCase Name", description = "The Web TestCase name", type = AFieldType.STRING )
		public final static String TESTCASENAME = "Web TestCase Name";

		@AField( description = "", type = AFieldType.BOOLEAN, enabled = true )
		public final static String STARTRECORDING = "Start Recording immediately";

	}
}
