import net.sf.json.JSONArray
import net.sf.json.JSONObject

def call(Map params = [:]) {

    String[] paths = params.paths
    String[] files = sh (returnStdout: true, script: 'find ' + paths.join(" ") + ' -name "*Integration.php" -exec sh -c "grep -Ho \'function test\' {} | uniq -c"  \\; | sed "s/:function test//"').tokenize('\n')
    JSONArray messages = new JSONArray()

    for (line in files) {
        String[] file = line.tokenize(' ')
        JSONObject message = new JSONObject()

        message.put("name",file[1])
        message.put("commands",[
            [
                container: "main",
                junit: [in: "/tmp/pod/", name: "junit_output.xml"],
                script: "su -s /bin/sh www-data -c 'php -d error_reporting=\'E_ALL\' vendor/bin/phpunit -c app/phpunit.xml.dist " + file[1] + " --log-junit /tmp/pod/junit_output.xml'"
            ]
        ])
        messages.add(message)
    }

    return messages
}
