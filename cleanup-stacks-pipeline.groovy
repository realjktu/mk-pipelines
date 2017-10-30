/**
 *
 * Delete stacks by Stack name after retention period
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
 *   STACK_NAMES_LIST              Stacks names comma separated list to inspect outdated stacks
 *   DRY_RUN			   Do not perform actual cleanup
 *
 *
 */



openstack = new com.mirantis.mk.Openstack()
import java.text.SimpleDateFormat

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
            def jobNames = STACK_NAMES_LIST.tokenize(',')
            ArrayList<String> existingStacks = []
            String outdatedStacks=""
            // Get list of stacks
            for (jobName in jobNames){
                existingStacks.addAll(openstack.getStacksForNameContains(openstackCloud, jobName, venv))
            }
            println 'Found ' + existingStacks.size() + ' stacks'
            // Check each stack
            def  toSeconds = 1000
            long currentTimestamp = (long) new Date().getTime() / toSeconds
            for (stackName in existingStacks){
                def stackInfo = openstack.getHeatStackInfo(openstackCloud, stackName, venv)
                //println stackInfo
                println 'Stack: ' + stackName + ' Creation time: ' + stackInfo.creation_time
                Date creationDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH).parse(stackInfo.creation_time.trim())
                //Date creationDate = new Date().parse("yyyy-MM-dd'T'HH:mm:ss'Z'", stackInfo.creation_time)
                long creationTimestamp = (long) creationDate.getTime() / toSeconds
                def diff = currentTimestamp - creationTimestamp
                def retentionSec = Integer.parseInt(RETENTION_DAYS) * 86400
                if (diff > retentionSec){
                    println stackName + ' stack have to be deleted'
                    outdatedStacks = outdatedStacks + 'Stack: ' + stackName + ' Creation time: ' + stackInfo.creation_time + '\n'
                    if (DRY_RUN.toBoolean() == true)
                        println "Dry run mode. No real deleting"
                    else
                        openstack.deleteHeatStack(openstackCloud, stackName, venv)                    
                }
            }
            println 'The following stacks were deleted: \n' + outdatedStacks
        }
    } catch (Exception e) {
        currentBuild.result = 'FAILURE'
        throw e
    }
}
