/**
 *
 * Delete stacks by Jenkins job name after retention period
 *
 * Expected parameters:
 *   OPENSTACK_API_URL             OpenStack API address
 *   OPENSTACK_API_CREDENTIALS     Credentials to the OpenStack API
 *   OPENSTACK_API_PROJECT         OpenStack project to connect to
 *   OPENSTACK_API_CLIENT          Versions of OpenStack python clients
 *   OPENSTACK_API_VERSION         Version of the OpenStack API (2/3)
 *   OPENSTACK_API_PROJECT_DOMAIN  OpenStack project domain
 *   OPENSTACK_API_PROJECT_ID      OpenStack project do id
 *   OPENSTACK_API_USER_DOMAIN     OpenStack user domain
 * 
 *   RETENTION_DAYS                Days to delete stacks after creation
 *   JOB_NAME                      Jenkins job name to inspect
 *
 * 
 */


//java.util.Date t1=new java.util.Date()
//import java.util.Date
t1=new Date()
t1.parse('1973/07/21')


openstack = new com.mirantis.mk.Openstack()
common = new com.mirantis.mk.Common()
//import java.text.SimpleDateFormat
//import java.util.Date 

def aa="2017-08-30"
//Date creationDate1 = Date.parse("yyyy-MM-dd", aa)
//Date creationDate1 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(aa)
//def newDate= Date.parse("yyyy-MM-dd", aa).format('MM/dd/YYYY')
//Date dd=new Date()
//dd.parse('yyyy/MM/dd', '1973/07/21')

println "t1"
node ('python') {
    try {
        stage('Looking for stacks to be deleted') {
            venv = "${env.WORKSPACE}/venv"
            openstack.setupOpenstackVirtualenv(venv, OPENSTACK_API_CLIENT)
            openstackCloud = openstack.createOpenstackEnv(
                OPENSTACK_API_URL, OPENSTACK_API_CREDENTIALS,
                OPENSTACK_API_PROJECT, OPENSTACK_API_PROJECT_DOMAIN,
                OPENSTACK_API_PROJECT_ID, OPENSTACK_API_USER_DOMAIN,
                OPENSTACK_API_VERSION)
            openstack.getKeystoneToken(openstackCloud, venv)
            String[] jobNames = JOB_NAME.split(',')
            ArrayList<String> existingStacks=new ArrayList<String>()
            
            // Get list of stacks
            for(def i=0;i<jobNames.size();i++){
                existingStacks.addAll(openstack.getStacksForNameContains(openstackCloud, jobNames[i], venv))
                println jobNames[i]
            }
            common.infoMsg("Found "+existingStacks.size()+" stacks")
            // Check each stack
//            long currentTimestamp = (long) new Date().getTime()/1000;
long currentTimestamp=11111
            for(def i=0;i<existingStacks.size();i++){
                def stackName = existingStacks.get(i)
                def stackInfo = openstack.getHeatStackInfo(openstackCloud, stackName, venv)
                //println stackInfo
                common.infoMsg("Stack: "+stackName+" Creation time: "+ stackInfo.creation_time)
            
                //Date creationDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(stackInfo.creation_time.trim())
		//Date creationDate = new Date().parse("yyyy-MM-dd'T'HH:mm:ss'Z'", stackInfo.creation_time)
                Date creationDate = stackInfo.creation_time
                long creationTimestamp = (long) creationDate.getTime()/1000
                def diff = currentTimestamp-creationTimestamp
                def retentionSec = 	Integer.parseInt(RETENTION_DAYS)*86400
                if (diff > retentionSec){
                    common.infoMsg(stackName+" stack have to be deleted")
                    //openstack.deleteHeatStack(openstackCloud, stackName, venv)hhhhh
                }
            }
        }
    } catch (Exception e) {
        currentBuild.result = 'FAILURE'
        throw e
    }
    
    
    
}
