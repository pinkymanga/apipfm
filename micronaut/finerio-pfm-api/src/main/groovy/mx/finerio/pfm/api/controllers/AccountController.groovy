package mx.finerio.pfm.api.controllers

import com.fasterxml.jackson.core.JsonProcessingException
import grails.gorm.transactions.Transactional
import io.micronaut.context.MessageSource
import io.micronaut.core.convert.exceptions.ConversionErrorException
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.*
import io.micronaut.validation.Validated
import io.reactivex.Single
import mx.finerio.pfm.api.dtos.AccountDto
import mx.finerio.pfm.api.dtos.ErrorDto
import mx.finerio.pfm.api.dtos.ResourcesDto
import mx.finerio.pfm.api.exceptions.NotFoundException
import mx.finerio.pfm.api.services.AccountService
import mx.finerio.pfm.api.services.UserService
import mx.finerio.pfm.api.validation.AccountCommand

import javax.annotation.Nullable
import javax.inject.Inject
import javax.validation.ConstraintViolationException
import javax.validation.Valid
import javax.validation.constraints.NotNull

@Controller("/accounts")
@Validated
class AccountController {

    @Inject
    AccountService accountService

    @Inject
    MessageSource messageSource

    @Post("/")
    Single<AccountDto> save(@Body @Valid AccountCommand cmd){
        Single.just(new AccountDto(accountService.create(cmd)))
    }

    @Get("/{id}")
    @Transactional
    Single<AccountDto> show(@NotNull Long id) {
        Single.just(new AccountDto(accountService.getAccount(id)))
    }

    @Get("{?cursor}")
    @Transactional
    Single<Map> showAll(@Nullable Long cursor) {
        List<AccountDto> accounts = cursor ? accountService.findAllByCursor(cursor) : accountService.getAll()
        Single.just(accounts.isEmpty() ? [] :  new ResourcesDto(accounts)) as Single<Map>
    }

    @Put("/{id}")
    Single<AccountDto> edit(@Body @Valid AccountCommand cmd, @NotNull Long id ) {
        Single.just(new AccountDto(accountService.update(cmd, id)))
    }

    @Delete("/{id}")
    @Transactional
    HttpResponse delete(@NotNull Long id) {
        accountService.delete(id)
        HttpResponse.noContent()
    }

    @Error
    HttpResponse<List<ErrorDto>> jsonError(ConstraintViolationException constraintViolationException) {
        HttpResponse.<List<ErrorDto>> status(HttpStatus.BAD_REQUEST,
                messageBuilder("request.body.invalid.title").get()).body(
                constraintViolationException.constraintViolations.collect {
                    new ErrorDto(it.message, messageSource)
                })
    }

    @Error(status = HttpStatus.BAD_REQUEST)
    HttpResponse<ErrorDto> badRequest(HttpRequest request) {
        HttpResponse.<ErrorDto>badRequest().body(
                new ErrorDto('request.body.invalid', this.messageSource)
        )
    }

    @Error(exception = NotFoundException)
    HttpResponse notFound(NotFoundException ex) {
        HttpResponse.notFound().body(ex.message)
    }

    @Error(exception = JsonProcessingException)
    HttpResponse<ErrorDto> badRequest(JsonProcessingException ex) {
        badRequestResponse()
    }

    @Error(exception = ConversionErrorException)
    private MutableHttpResponse<ErrorDto> badRequestResponse() {
        HttpResponse.<ErrorDto> badRequest().body(new ErrorDto('request.body.invalid', this.messageSource))
    }

    private Optional<String> messageBuilder(String code) {
        messageSource.getMessage(code, MessageSource.MessageContext.DEFAULT)
    }

}