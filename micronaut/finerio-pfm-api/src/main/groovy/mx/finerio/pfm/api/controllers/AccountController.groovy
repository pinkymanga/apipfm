package mx.finerio.pfm.api.controllers


import grails.gorm.transactions.Transactional
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.*
import io.micronaut.security.annotation.Secured
import io.micronaut.validation.Validated
import io.reactivex.Single
import mx.finerio.pfm.api.dtos.AccountDto

import mx.finerio.pfm.api.dtos.ResourcesDto
import mx.finerio.pfm.api.logging.Log
import mx.finerio.pfm.api.services.AccountService
import mx.finerio.pfm.api.validation.AccountCreateCommand
import mx.finerio.pfm.api.validation.AccountUpdateCommand

import javax.annotation.Nullable
import javax.inject.Inject
import javax.validation.Valid
import javax.validation.constraints.NotNull

@Controller("/accounts")
@Validated
@Secured('isAuthenticated()')
class AccountController {

    @Inject
    AccountService accountService

    @Log
    @Post("/")
    @Transactional
    Single<AccountDto> save(@Body @Valid AccountCreateCommand cmd){
        Single.just(new AccountDto(accountService.create(cmd)))
    }

    @Log
    @Get("/{id}")
    @Transactional
    Single<AccountDto> show(@NotNull Long id) {
        Single.just(new AccountDto(accountService.getAccount(id)))
    }

    @Log
    @Get("{?cursor}")
    @Transactional
    Single<ResourcesDto> showAll(@Nullable Long cursor, @QueryValue('userId') Long userId ) {
        List<AccountDto> accounts = cursor ?
                accountService.findAllByUserAndCursor(userId, cursor)
                : accountService.findAllAccountDtosByUser(userId)
        Single.just( new ResourcesDto(accounts))
    }

    @Log
    @Put("/{id}")
    @Transactional
    Single<AccountDto> edit(@Body AccountUpdateCommand cmd, @NotNull Long id ) {
        Single.just(new AccountDto(accountService.update(cmd, id)))
    }

    @Log
    @Delete("/{id}")
    @Transactional
    HttpResponse delete(@NotNull Long id) {
        accountService.delete(id)
        HttpResponse.noContent()
    }

}
