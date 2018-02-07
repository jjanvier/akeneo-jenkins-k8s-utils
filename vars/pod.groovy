import org.csanchez.jenkins.plugins.kubernetes.pipeline.PodTemplateAction
import org.csanchez.jenkins.plugins.kubernetes.ContainerTemplate

def call(Map params = [:], Closure body) {
    String label = params.get('label', null)
    String uuid = UUID.randomUUID().toString()

    // see https://issues.jenkins-ci.org/browse/JENKINS-42184
    currentBuild.rawBuild.getAction( PodTemplateAction.class )?.stack?.clear()

    timeout(time: 4, unit: 'HOURS') {
        return steps.podTemplate(label: label + "-" + uuid, containers: params.get('containers', [])) {
            node(label + "-" + uuid) {
                body()
            }
        }
    }
}
