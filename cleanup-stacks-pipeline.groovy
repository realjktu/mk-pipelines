/**
 *
 * Delete stacks or notify users by Stack name after retention period
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
 *   RETENTION_DAYS                Days to delete stacks after creation
 *   DRY_RUN                       Do not perform actual cleanup
 *   SEND_NOTIFICATIONS            Send notifications. Do not delete stacks if True.
 *   STACK_NAMES_LIST              Stacks names comma separated list to inspect outdated stacks.
 *
 *
 */



openstack = new com.mirantis.mk.Openstack()
import java.text.SimpleDateFormat
node ('python') {
    try {
        HashMap<String, String> outdatedStacks = [:]
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
            ArrayList<String> candidateStacksToDelete = []
            String outdatedStacks=""
            // Get list of stacks
            for (jobName in jobNames){
                candidateStacksToDelete.addAll(openstack.getStacksForNameContains(openstackCloud, jobName, venv))
            }
            println 'Found ' + candidateStacksToDelete.size() + ' stacks'
            // Check each stack
            def  toSeconds = 1000
            long currentTimestamp = (long) new Date().getTime() / toSeconds
            for (stackName in candidateStacksToDelete){
                def stackInfo = openstack.getHeatStackInfo(openstackCloud, stackName, venv)
                //println stackInfo
                println 'Stack: ' + stackName + ' Creation time: ' + stackInfo.creation_time
                Date creationDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH).parse(stackInfo.creation_time.trim())
                long creationTimestamp = (long) creationDate.getTime() / toSeconds
                def diff = currentTimestamp - creationTimestamp
                def retentionSec = Integer.parseInt(RETENTION_DAYS) * 86400
                if (diff > retentionSec){
                    String stackOwner = stackName.split('-')[0]
                    if (SEND_NOTIFICATIONS.toBoolean()){
                        String stackLink='https://cloud-cz.bud.mirantis.net/project/stacks/stack/'+stackInfo.id                        
                        String stackDetails='{"title":"' + stackName + '", "title_link": "' + stackLink + '", "footer": "Created at: ' + stackInfo.creation_time.replace('Z', '').replace('T', ' ') + '"}'
                        if (outdatedStacks.containsKey(stackOwner)){
                            outdatedStacks.put(stackOwner, outdatedStacks.get(stackOwner) + ',' + stackDetails)
                        } else {
                            outdatedStacks.put(stackOwner, stackDetails)
                        }
                    }else{
                        def buildUsername = env.BUILD_USER_ID
                        if (buildUsername.compareTo('jenkins') == 0 || buildUsername.compareTo(stackOwner) == 0){
                            println stackName + ' stack have to be deleted'                        
                            outdatedStacks = outdatedStacks + 'Stack: ' + stackName + ' Creation time: ' + stackInfo.creation_time + '\n'
                            if (DRY_RUN.toBoolean() == true)
                                println "Dry run mode. No real deleting"
                            else
                                ooooooopenstack.deleteHeatStack(openstackCloud, stackName, venv)
                        }
                    }
                }
            }
            println 'The following stacks were deleted: \n' + outdatedStacks
        }
        stage('Sending messages') {
            if (SEND_NOTIFICATIONS.toBoolean()){
                for (Map.Entry<String, String> entry : outdatedStacks.entrySet()) {
                    String stackOwner = entry.getKey();
                    String stacks = entry.getValue();
                    String msg = '{"text": "Hi @' + stackOwner + ' ! Please consider to delete the following '+OPENSTACK_API_PROJECT+' old (created more than ' + RETENTION_DAYS + ' days ago) stacks:", "attachments": [ ' + stacks + ']}'
                    println msg
                    println '--------------------------------------------------'        
                    sh 'curl -X POST -H \'Content-type: application/json\' --data \'' + msg + '\' ' + SLACK_API_URL
                }
            }
        }
    } catch (Exception e) {
        currentBuild.result = 'FAILURE'
        throw e
    }
}
