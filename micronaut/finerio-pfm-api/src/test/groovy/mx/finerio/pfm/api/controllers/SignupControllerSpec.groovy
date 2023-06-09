package mx.finerio.pfm.api.controllers

import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxStreamingHttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.annotation.MicronautTest
import mx.finerio.pfm.api.Application
import mx.finerio.pfm.api.domain.ClientProfile
import mx.finerio.pfm.api.services.gorm.ClientGormService
import mx.finerio.pfm.api.services.gorm.ClientProfileGormService
import mx.finerio.pfm.api.services.gorm.ClientRoleGormService
import mx.finerio.pfm.api.services.gorm.RoleGormService
import mx.finerio.pfm.api.validation.SignupCommand
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject

@Property(name = 'spec.name', value = 'signup controller')
@MicronautTest(application = Application.class)
class SignupControllerSpec  extends Specification {

    public static final String SIGNUP_ROOT = "/signup"

    @Shared
    @Inject
    @Client("/")
    RxStreamingHttpClient client

    @Shared
    @Inject
    ClientProfileGormService clientProfileGormService

    @Shared
    @Inject
    ClientGormService clientGormService

    @Shared
    @Inject
    ClientRoleGormService clientRoleGormService

    @Shared
    @Inject
    RoleGormService roleGormService

    void cleanup(){
        def client = clientGormService.findByUsername('SignupControllerSpec')
        ClientProfile clientProfile = clientProfileGormService.findByClient(client)
        clientProfileGormService.delete(clientProfile.id)
        clientRoleGormService.delete(client)
        clientGormService.delete(client.id)
    }

    def "Should do a signup"(){
        given:
        SignupCommand cmd = new SignupCommand()
        cmd.with {
            name = "test name"
            firstLastName = "test first name"
            secondLastName = "second last name"
            email = "a@q.com"
            companyName = "ACME inc"
            username = "SignupControllerSpec"
            password = "qwerty"
        }

        and:
        HttpRequest request = HttpRequest.POST(SIGNUP_ROOT, cmd)

        when:
        def rsp = client.toBlocking().exchange(request)

        then:
        rsp.status == HttpStatus.OK

    }

}
