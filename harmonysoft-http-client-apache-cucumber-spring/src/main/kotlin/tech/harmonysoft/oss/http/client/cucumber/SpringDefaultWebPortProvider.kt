package tech.harmonysoft.oss.http.client.cucumber

import org.springframework.boot.test.web.server.LocalServerPort
import javax.inject.Named

@Named
class SpringDefaultWebPortProvider : DefaultWebPortProvider {

    @LocalServerPort override var port = 0
}