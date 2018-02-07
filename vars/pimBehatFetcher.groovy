import net.sf.json.JSONArray
import net.sf.json.JSONObject

def call(Map params = [:]) {

    JSONArray messages = new JSONArray()
    String[] profiles = params.get('profiles', ["default"])
    String[] features = params.features

    for (profile in profiles) {
        def scenarios = sh (returnStdout: true, script: "cd /var/www/pim/ && vendor/bin/behat -p ${profile} --list-scenarios").tokenize('\n')

        for (scenario in scenarios) {
            for (feature in features) {
                if (scenario.startsWith(feature) || scenario.startsWith("/var/www/pim/" + feature)) {
                    JSONObject message = new JSONObject()
                    message.put("name",scenario)
                    message.put("commands",[
                        [
                            container: "main",
                            junit: [in: "/tmp/pod/", name: "*.xml"],
                            artifacts: [in: "/tmp/pod/", name: "*.png"],
                            script: "su -s /bin/sh www-data -c 'php vendor/bin/behat --strict -p " + profile + " -vv " + scenario + "'"
                        ]
                    ])
                    messages.add(message)

                    break
                }
            }
        }
    }

    return messages
}
