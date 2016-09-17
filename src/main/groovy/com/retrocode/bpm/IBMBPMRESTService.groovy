package com.retrocode.bpm

import groovy.json.JsonSlurper

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

/**
 * Used to update variables on a process instance based on complex objects in IBM BPM using the exposed REST API
 * See http://www.ibm.com/developerworks/websphere/library/techarticles/1108_thaker/1108_thaker.html for more info on the Rest API exposed
 */
class IBMBPMRESTService {

	private static final Logger log = LogManager.getLogger(IBMBPMRESTService.class);


	public void updateBPDVariables(String hostName,HashMap processIds,String variableName,newVariableValue){

		processIds.keySet().each {

			log.info("Updating process id ${it} - ${variableName}=${newVariableValue}")
			newVariableValue = java.net.URLEncoder.encode(newVariableValue,'ISO-8859-1')
			def urlStr = "${hostName}/rest/bpm/wle/v1/process/${it}?action=js&script=${variableName}='${newVariableValue}'"
			log.info("The url is ${urlStr}")
			log.info("Preparing for PUT request")
			def response = sendPutRequest(urlStr)
			// respose comes back with " around the variable
			//assert response?.result.contains(newVariableValue)



		}
	}

	// not called directly
	private updateTaskInstanceVariables(String hostName,String tasksId,String params){

		def failureMode = "embedded"
		params = java.net.URLEncoder.encode(params,'ISO-8859-1')
		def urlStr = "${hostName}/rest/bpm/wle/v1/service/${tasksId}"
		def action = "setData"
		log.info("The url is ${urlStr}")
		def response = sendPostRequest(urlStr,action,params)
			
			
		
	}

	public getTaskInstanceVariables(String hostName,String taskId,String variableName){

		log.info("Getting task id ${taskId} - ${variableName}")

		def urlStr = "${hostName}/rest/bpm/wle/v1/service/${taskId}?action=getData&fields=${variableName}"

		log.info("The url is ${urlStr}")
		def response = sendGetRequest(urlStr)

		response
	}
	
	public getTaskDetails(String hostName,String taskId,userName,password){
		
				log.info("Getting task ${taskId} - details")
		
				def urlStr = "${hostName}/rest/bpm/wle/v1/${taskId}?parts=all"
		
				log.info("The url is ${urlStr}")
				def response = sendRequest(urlStr,userName,password,"GET")
		
				response
	}
	
	
	
	
	/**
	 * This code is used to update task variables within BPM
	 * Experimentation has show when doing this with a complex variable, ie. CD.Iteration.something this is a 3 step process:
	 * 	1) the FULL variable JSON must be read
	 *  2) the specific variable must be changed within the JSON string
	 *  3) the FULL updated variable must be passed "back in" 
	 * @param hostName
	 * @param tasksIds - a map of task ids
	 * @param baseVariableName - the base variable ie. cd
	 * @param variableToUpdate - the variable ie cd.SubDivisionName
	 * @param newVariableValue - the new value
	 * @return
	 */
	public getTaskAndUpdateVariables(String hostName,HashMap tasksIds,String baseVariableName,String variableToUpdate,String newVariableValue){

		def failureMode = "embedded"

		// loop through each key in the hashmap		
		tasksIds.keySet().each {

			log.info("Updating tasks id ${it} baseVariableName:${baseVariableName} variableToUpdate:${variableToUpdate} newVariableValue:${newVariableValue}")
			
			def response = getTaskInstanceVariables(hostName, "${it}",baseVariableName)
			def result = response.result.toString()
			
			// convert to json object so we can access the object we want
			def jsonObject = new JsonSlurper().parseText(result)
			
			def objectToUpdate
			
			// ***** this is crazy Groovy code....because this is intended to be dynamic for any variable at any level
			
			// so if it starts like this
			//cd.Iteration.Priority.ID
			
			// it needs to be evaluated like this - THIS IS A MAP OF MAPS and this notation is legal in Groovy!
			//['cd']['Iteration']['Priority']['ID']
			
			def indexStr = ""
			
			// make the index string in the right form
			def elements = variableToUpdate.tokenize('.').each{
				indexStr+="['${it}']"
			}
			
			log.debug("The index string is: ${indexStr}")
			
			// this is a string of code that we are going to evaluate - set the variable in a map of maps, then return it			
			def expression = "params.jsonObject${indexStr}=params.newValue;return params.jsonObject"
			def values = ['jsonObject':jsonObject,'newValue':"${newVariableValue}"]
			
			// evaluate the string - ie. execute the code within the string
			def updatedResult = Eval.me("params", values, expression)
			
			log.debug("After the variable update the updatedResult is: ${updatedResult}")
			
			def json = new groovy.json.JsonBuilder( updatedResult )
			def resultString = json.toString()
			
			updateTaskInstanceVariables(hostName, "${it}", resultString )
			
			
		}
	}

	
	
	private sendGetRequest(String urlStr,String userName,String password) throws Exception{
		sendRequest(urlStr,userName,password,"GET")
	}
	
	private sendPutRequest(String urlStr,String userName,String password) throws Exception{
		sendRequest(urlStr,userName,password,"PUT")
	}
	
	
	
	
	private sendRequest(String urlStr,String userName,String password,String verb) throws Exception{
		
		HttpURLConnection connection;
		def result
		InputStreamReader inReader
		BufferedReader br
		def responseCode
		def responseMessage

		try {

			log.info("sendRequest ${verb}->")
			URL url = new URL(urlStr);
			connection = (HttpURLConnection) url.openConnection();
			String authorization = "Basic " + new String(Base64.encoder.encode(new String(userName + ":" + password).getBytes()));
			connection.setRequestProperty("Authorization", authorization);

			//connection.setRequestProperty("Content-Type", "application/json");
			connection.setRequestProperty("Content-Type",	"application/x-www-form-urlencoded");
			
			connection.setRequestMethod(verb);

			responseCode = connection.getResponseCode()
			responseMessage = connection.getResponseMessage()

			if (responseCode!=200){
				log.error("Error returned from REST API - ${responseCode}:${responseMessage}")
				throw new Exception("Error returned from REST API - ${responseCode}:${responseMessage}" )
			}
			
			// get the response
			log.info("Response code: ${responseCode}:${responseMessage}")
			inReader = new InputStreamReader(connection.getInputStream());
			br = new BufferedReader(inReader);
			String line = br.readLine();
			
			def resultJSON = new JsonSlurper().parseText(line)
			result = resultJSON?.data

		} catch (MalformedURLException e) {
			e.printStackTrace();
			throw e;
		} catch (ProtocolException e) {
			e.printStackTrace();
			throw e;
		} catch (IOException e) {
			e.printStackTrace();
			throw e;
		}
		finally
		{
			//close the connection, set all objects to null
			connection.disconnect();
			inReader = null;
			br = null;
			connection = null;
			log.info("<-sendRequest ${verb}")
		}

		result
	}
	
	private sendPostRequest(String urlStr,String userName,String password,String action,String params) throws Exception{
		
		log.info("REST POST request urlStr=${urlStr} action=${action}")
		
		HttpURLConnection connection;
		def result
		InputStreamReader inReader
		BufferedReader br
		def responseCode
		def responseMessage
		def writer

		try {

			log.info("sendPostRequest->")
			URL url = new URL(urlStr);
			connection = (HttpURLConnection) url.openConnection();
			String authorization = "Basic " + new String(org.apache.commons.codec.binary.Base64.encodeBase64(new String(userName + ":" + password).getBytes()));
			connection.setRequestProperty("Authorization", authorization);

			connection.setRequestProperty("Content-Type", "application/json");
			connection.setRequestProperty("Content-Type",	"application/x-www-form-urlencoded");
			
			connection.setRequestMethod("POST");
			connection.setDoOutput(true);
						
			writer = new OutputStreamWriter(connection.getOutputStream());
			
			// format required for REST setData using POST - http://publib.boulder.ibm.com/infocenter/dmndhelp/v7r5m1/index.jsp?topic=%2Fcom.ibm.wbpm.bspace.ref.doc%2Fbpmrest-docs%2Frest_bpm_wle_v1_task_taskid_get_getdata.htm
			def urlParameters = "action=${action}&params=${params}"
			writer.write(urlParameters);
			writer.flush();
			
			responseCode = connection.getResponseCode()
			responseMessage = connection.getResponseMessage()

			if (responseCode!=200){
				log.error("Error returned from REST API - ${responseCode}:${responseMessage}")
				throw new Exception("Error returned from REST API - ${responseCode}:${responseMessage}" )
			}
			
			// get the response
			log.info("Response code: ${responseCode}:${responseMessage}")
			inReader = new InputStreamReader(connection.getInputStream());
			br = new BufferedReader(inReader);
			String line = br.readLine();
			
			def resultJSON = new JsonSlurper().parseText(line)
			result = resultJSON?.data



		} catch (MalformedURLException e) {
			e.printStackTrace();
			throw e;
		} catch (ProtocolException e) {
			e.printStackTrace();
			throw e;
		} catch (IOException e) {
			e.printStackTrace();
			throw e;
		}
		finally
		{
			//close the connection, set all objects to null
			writer.close();
			connection.disconnect();
			inReader = null;
			br = null;
			connection = null;
			writer = null;
			log.info("<-sendPostRequest")
			
		}

		result
	}
		
		
	
	
	
	/**
	 * default
	 */
	private sendGetRequest(String urlStr) throws Exception{
		
		sendGetRequest(urlStr,"bpmadmin","bpmadmin")
		
	}
		
	private sendPutRequest(String urlStr) throws Exception{
		
		sendPutRequest(urlStr,"bpmadmin","bpmadmin")
		
	}
	
	private sendPostRequest(String urlStr,action,params) throws Exception{
		
		sendPostRequest(urlStr,"bpmadmin","bpmadmin",action,params)
		
	}
		
}
