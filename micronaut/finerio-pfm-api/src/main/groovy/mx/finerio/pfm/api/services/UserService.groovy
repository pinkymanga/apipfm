package mx.finerio.pfm.api.services

import mx.finerio.pfm.api.domain.User
import grails.gorm.services.Service

@Service(User)
interface UserService {
    User save(User user)
}