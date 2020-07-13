package mx.finerio.pfm.api.services.imp

import grails.gorm.transactions.Transactional
import mx.finerio.pfm.api.domain.Client
import mx.finerio.pfm.api.domain.User
import mx.finerio.pfm.api.dtos.UserDto
import mx.finerio.pfm.api.exceptions.ItemNotFoundException
import mx.finerio.pfm.api.services.UserService
import mx.finerio.pfm.api.services.gorm.UserGormService
import mx.finerio.pfm.api.validation.UserCommand
import org.springframework.stereotype.Service

import javax.inject.Inject

@Service
class UserServiceImp extends ServiceTemplate implements UserService {

    @Inject
    UserGormService userGormService

    @Override
    User getUser(long id) {
        Optional.ofNullable(userGormService.findByIdAndClientAndDateDeletedIsNull(id, getCurrentLoggedClient()))
                .orElseThrow({ -> new ItemNotFoundException('user.notFound') })
    }

    @Override
    User create(UserCommand cmd, Client client) {
        if ( !cmd  ) {
            throw new IllegalArgumentException('request.body.invalid' )
        }
        User user = userGormService.findByNameAndAndClientAndDateDeletedIsNull(cmd.name, client)
        if(!user){
            user = new User(cmd.name, client)
            return userGormService.save(user)
        }
        user
    }

    @Override
    @Transactional
    User update(UserCommand cmd, Long id){
        User user = getUser(id)
        user.with {
            name = cmd.name
        }
        userGormService.save(user)
    }

    @Override
    @Transactional
    void delete(Long id){

        User user = getUser(id)
        user.with {
            dateDeleted = new Date()
        }
        userGormService.save(user)

    }

    @Override
    @Transactional
    List<UserDto> getAllByClient(Client client) {
        userGormService.findAllByClientAndDateDeletedIsNull(client, [max: MAX_ROWS, sort: 'id', order: 'desc'])
                .collect{user -> new UserDto(user)}
    }

    @Override
    @Transactional
    List<UserDto> getAllByClientAndCursor(Client client, Long cursor) {
        userGormService.findAllByClientAndDateDeletedIsNullAndIdLessThanEquals(client,cursor,
                [max: MAX_ROWS, sort: 'id', order: 'desc'])
                .collect{user -> new UserDto(user)}
    }

    @Override
    List<UserDto> findAllByCursor(long cursor) {
        userGormService.findAllByDateDeletedIsNullAndIdLessThanEquals(cursor, [max: MAX_ROWS, sort: 'id', order: 'desc'])
                .collect{new UserDto(it)}
    }
}
