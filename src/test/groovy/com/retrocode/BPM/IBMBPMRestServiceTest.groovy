package com.retrocode.BPM

import com.retrocode.bpm.IBMBPMRESTService
import junit.framework.TestCase
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

/**
 * Created by rhett on 2016-09-16.
 */
class IBMBPMRestServiceTest extends TestCase{


    IBMBPMRESTService service = new IBMBPMRESTService()
    private static final Logger log = LogManager.getLogger('RESTServiceIntegrationTest');
    def hostname = "http://localhost:9081"

    def taskId = "13324"
    def processId = "2639"


    public void testUpdateBPDVariables(){

        def variableName = "tw.local.cd.PhaseNumber"
        def newvalue = "newphase"
        def list = ["${processId}":null]
        service.updateBPDVariables(hostname, list,variableName,newvalue )


    }
}
