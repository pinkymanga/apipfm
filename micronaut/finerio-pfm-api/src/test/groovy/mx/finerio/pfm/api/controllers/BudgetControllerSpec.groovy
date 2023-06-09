package mx.finerio.pfm.api.controllers

import io.micronaut.context.annotation.Property
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxStreamingHttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.security.token.jwt.render.AccessRefreshToken
import io.micronaut.test.annotation.MicronautTest
import mx.finerio.pfm.api.Application
import mx.finerio.pfm.api.domain.*
import mx.finerio.pfm.api.dtos.resource.BudgetDto
import mx.finerio.pfm.api.dtos.resource.CategoryDto
import mx.finerio.pfm.api.dtos.resource.TransactionDto
import mx.finerio.pfm.api.dtos.utilities.ErrorDto
import mx.finerio.pfm.api.dtos.utilities.ErrorsDto
import mx.finerio.pfm.api.exceptions.ItemNotFoundException
import mx.finerio.pfm.api.services.ClientService
import mx.finerio.pfm.api.services.gorm.*
import mx.finerio.pfm.api.validation.BudgetCreateCommand
import mx.finerio.pfm.api.validation.BudgetUpdateCommand
import mx.finerio.pfm.api.validation.CategoryCreateCommand
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject

@Property(name = 'spec.name', value = 'budget controller')
@MicronautTest(application = Application.class)
class BudgetControllerSpec extends Specification {

    public static final String BUDGETS_ROOT = "/budgets"
    public static final String LOGIN_ROOT = "/login"

    @Shared
    @Inject
    @Client("/")
    RxStreamingHttpClient client

    @Inject
    UserGormService userGormService

    @Inject
    @Shared
    TransactionGormService transactionGormService

    @Inject
    @Shared
    CategoryGormService categoryGormService

    @Inject
    @Shared
    BudgetGormService budgetGormService

    @Inject
    @Shared
    ClientService clientService

    @Inject
    @Shared
    AccountGormService accountGormService

    @Inject
    @Shared
    FinancialEntityGormService financialEntityGormService

    @Shared
    String accessToken

    @Shared
    mx.finerio.pfm.api.domain.Client loggedInClient

    def setupSpec(){
        def generatedUserName = this.getClass().getCanonicalName()
        loggedInClient = clientService.register( generatedUserName, 'elementary', ['ROLE_ADMIN'])
        HttpRequest request = HttpRequest.POST(LOGIN_ROOT, [username:generatedUserName, password:'elementary'])
                .bearerAuth(accessToken)
        def rsp = client.toBlocking().exchange(request, AccessRefreshToken)
        accessToken = rsp.body.get().accessToken

    }

    void cleanup() {
        List<Budget> budgetList = budgetGormService.findAll()
        budgetList.each {
            budgetGormService.delete(it.id)
        }

        List<Transaction> transactions = transactionGormService.findAll()
        transactions.each {
            transactionGormService.delete(it.id)
        }

        List<Category> categoriesChild = categoryGormService.findAllByParentIsNotNull()
        categoriesChild.each { Category category ->
            categoryGormService.delete(category.id)
        }

        List<Category> categories = categoryGormService.findAll()
        categories.each { Category category ->
            categoryGormService.delete(category.id)
        }

    }

    def "Should get a list of budgets in a cursor and had next cursor on non consecutive"() {

        given: 'a budget list'
        User user1 = generateUser()
        User user2 = generateUser()
        List<Budget> budgets = []

        Category category1 = generateCategory(user1)
        10.times {
            budgets.add(generateSavedCategoryBudget(user1, category1))
        }
        50.times {
            generateSavedCategoryBudget(user2, category1)
        }
        98.times {
            budgets.add(generateSavedCategoryBudget(user1, category1))
        }

        and:
        HttpRequest getReq = HttpRequest.GET("$BUDGETS_ROOT?cursor=${budgets.last().id}&userId=${user1.id}").bearerAuth(accessToken)

        when:
        def rspGET = client.toBlocking().exchange(getReq, Map)

        then:
        rspGET.status == HttpStatus.OK
        Map body = rspGET.getBody(Map).get()
        List<BudgetDto> budgetDtos= body.get("data") as List<BudgetDto>

        assert budgetDtos.first().id == budgets.last().id
        assert budgetDtos.last().id == budgets[8].id
        assert body.get("nextCursor") ==  budgets[7].id

    }

    def "Should get unauthorized"() {

        given:
        HttpRequest getReq = HttpRequest.GET(BUDGETS_ROOT)

        when:
        client.toBlocking().exchange(getReq, Map)

        then:
        def  e = thrown HttpClientResponseException
        e.response.status == HttpStatus.UNAUTHORIZED
    }

    def "Should get a empty list of budgets"() {

        given: 'a user without budgets'

        User user1 = generateUser()

        HttpRequest getReq = HttpRequest.GET("$BUDGETS_ROOT?userId=${user1.id}").bearerAuth(accessToken)

        when:
        def rspGET = client.toBlocking().exchange(getReq,  Map)

        then:
        rspGET.status == HttpStatus.OK
        Map body = rspGET.getBody(Map).get()
        assert !body.isEmpty()
        assert body.get("nextCursor") == null

        List<BudgetDto> budgetDtos= body.get("data") as List<BudgetDto>
        assert budgetDtos.isEmpty()

    }

    def "Should create a budget"() {
        given: 'an saved User '
        User user1 = generateUser()
        Category category1 = generateCategory(user1)

        and: 'a command request body'
        BudgetCreateCommand cmd = generateBudgetCommand(user1, category1)

        HttpRequest request = HttpRequest.POST(BUDGETS_ROOT, cmd).bearerAuth(accessToken)

        when:
        def rsp = client.toBlocking().exchange(request, BudgetDto)

        then:
        assert rsp.status == HttpStatus.OK
        rsp.body().with {
            assert name == cmd.name
            assert spent == 0
            assert leftToSpend == amount
            assert status == BudgetDto.StatusEnum.ok
            assert warningPercentage == 0.7F
            assert id
            assert amount.floatValue() == cmd.amount.floatValue()
        }

    }

    def "Should not create a budget and throw bad request on wrong params"() {
        given: 'a budget request body with empty body'

        HttpRequest request = HttpRequest.POST(BUDGETS_ROOT, new CategoryCreateCommand()).bearerAuth(accessToken)

        when:
        client.toBlocking().exchange(request, CategoryDto)

        then:
        def e = thrown HttpClientResponseException
        e.response.status == HttpStatus.BAD_REQUEST
    }

    def "Should not create a budget and throw bad request on duplicated category"() {
        given: 'an saved User '
        User user1 = generateUser()

        and:
        Category category1 = generateCategory(user1)

        and:'a previously saved budget with category 1'
        Budget budget = new Budget()
        budget.with {
            user = user1
            category = category1
            name = 'test budget'
            amount = 100.00
            warningPercentage = 0.7
        }
        budgetGormService.save(budget)

        and: 'a command request body with already saved category'
        BudgetCreateCommand cmd = generateBudgetCommand(user1, category1)

        HttpRequest request = HttpRequest.POST(BUDGETS_ROOT, cmd).bearerAuth(accessToken)

        when:
        client.toBlocking().exchange(request, Argument.of(CategoryDto) as Argument<CategoryDto>,
                Argument.of(ErrorsDto))

        then:
        def  e = thrown HttpClientResponseException
        e.response.status == HttpStatus.BAD_REQUEST

        when:
        Optional<ErrorsDto> jsonError = e.response.getBody(ErrorsDto)
        then:
        assert jsonError.isPresent()
        jsonError.get().errors.first().with {
            assert code == 'budget.category.nonUnique'
            assert title == 'Category already exist'
            assert detail == 'The category you provided already exist'
        }
    }

    def "Should not create a transaction and throw bad request on wrong body"() {
        given: 'a transaction request body with empty body'

        HttpRequest request = HttpRequest.POST(BUDGETS_ROOT, 'asd').bearerAuth(accessToken)

        when:
        client.toBlocking().exchange(request, CategoryDto)

        then:
        def e = thrown HttpClientResponseException
        e.response.status == HttpStatus.BAD_REQUEST
    }

    def "Should not create a budget and throw not found exception on user not found"() {
        given: 'an budget request body with no found account id'

        def user = new User()
        user.id = 666
        Category category = new Category()
        category.id = 666
        BudgetCreateCommand cmd = generateBudgetCommand(user, category)

        HttpRequest request = HttpRequest.POST(BUDGETS_ROOT, cmd).bearerAuth(accessToken)

        when:
        client.toBlocking().exchange(request, Argument.of(BudgetDto) as Argument<BudgetDto>, Argument.of(ErrorDto))

        then:
        def e = thrown HttpClientResponseException
        e.response.status == HttpStatus.NOT_FOUND
    }

    def "Should get a budget"() {
        given: 'a saved user1'
        User user1 = generateUser()

        and: 'a saved category'
        Category category1 = generateCategory(user1)

        and:
        Budget budget = new Budget()
        budget.with {
            user = user1
            category = category1
            name = 'test budget'
            warningPercentage = 0.7
        }
        budgetGormService.save(budget)

        and:
        HttpRequest getReq = HttpRequest.GET(BUDGETS_ROOT + "/${budget.id}").bearerAuth(accessToken)

        when:
        def rspGET = client.toBlocking().exchange(getReq, CategoryDto)

        then:
        rspGET.status == HttpStatus.OK
        assert rspGET.body().with {
            category1
        }
        assert !category1.dateDeleted

    }

    def "Should not get a transaction and throw 404"() {
        given: 'a not found id request'

        HttpRequest request = HttpRequest.GET("${BUDGETS_ROOT}/0000").bearerAuth(accessToken)

        when:
        client.toBlocking().exchange(request, Argument.of(TransactionDto) as Argument<TransactionDto>, Argument.of(ItemNotFoundException))

        then:
        def e = thrown HttpClientResponseException
        e.response.status == HttpStatus.NOT_FOUND

    }

    def "Should not get an account and throw 400"() {
        given: 'a not found id request'

        HttpRequest request = HttpRequest.GET("${BUDGETS_ROOT}/abc").bearerAuth(accessToken)

        when:
        client.toBlocking().exchange(request, TransactionDto)

        then:
        def e = thrown HttpClientResponseException
        e.response.status == HttpStatus.BAD_REQUEST

    }

    def "Should update an budget"() {
        given: 'a saved user'
        User user1 = generateUser()

        and: 'a saved category'
        Category category1 = new Category()
        category1.with {
            user = user1
            name = 'Shoes and clothes'
            color = "#00FFAA"
            category1.client = loggedInClient
        }
        categoryGormService.save(category1)

        and: 'another saved category'
        Category category2 = new Category()
        category2.with {
            user = user1
            name = 'another category'
            color = "#00FFAA"
            category2.client = loggedInClient
        }
        categoryGormService.save(category2)

        and:'a saved budget'
        Budget budget = generateSavedCategoryBudget(user1, category1)

        and:'a update command'
        BudgetCreateCommand cmd = new BudgetCreateCommand()
        cmd.with {
            userId = user1.id
            categoryId = category2.id
            name = 'changed name'
            amount = 100
            warningPercentage = 0.5
        }

        and: 'a client'
        HttpRequest request = HttpRequest.PUT("${BUDGETS_ROOT}/${budget.id}", cmd).bearerAuth(accessToken)

        when:
        def resp = client.toBlocking().exchange(request, Argument.of(BudgetDto) as Argument<BudgetDto>,
                Argument.of(ErrorsDto))
        then:
        resp.status == HttpStatus.OK
        resp.body().with {
           cmd
            assert warningPercentage == cmd.warningPercentage as float
        }

    }

    def "Should not update an budget and throw bad request on already created budget category"() {
        given: 'a saved user'
        User user1 = generateUser()

        and: 'a saved category'
        Category category1 = new Category()
        category1.with {
            user = user1
            name = 'Shoes and clothes'
            color = "#00FFAA"
            category1.client = loggedInClient
        }
        categoryGormService.save(category1)

        and:'a saved budget'
        Budget budget = generateSavedCategoryBudget(user1, category1)

        and:'a update command'
        BudgetCreateCommand cmd = new BudgetCreateCommand()
        cmd.with {
            userId = user1.id
            categoryId = category1.id
            name = 'changed name'
            amount = 100
        }

        and: 'a client'
        HttpRequest request = HttpRequest.PUT("${BUDGETS_ROOT}/${budget.id}", cmd).bearerAuth(accessToken)

        when:
        client.toBlocking().exchange(request, Argument.of(BudgetDto) as Argument<BudgetDto>,
                Argument.of(ErrorsDto))
        then:
        def e = thrown HttpClientResponseException
        e.response.status == HttpStatus.BAD_REQUEST

        when:
        Optional<ErrorsDto> jsonError = e.response.getBody(ErrorsDto)
        then:
        assert jsonError.isPresent()
        jsonError.get().errors.first().with {
            assert code == 'budget.category.nonUnique'
            assert title == 'Category already exist'
            assert detail == 'The category you provided already exist'
        }

    }

    def "Should partially update a budget"() {
        given: 'a saved user'
        User user1 = generateUser()

        and: 'a saved category'
        Category category1 = new Category()
        category1.with {
            user = user1
            name = 'Shoes and clothes'
            color = "#00FFAA"
            category1.client = loggedInClient
        }
        categoryGormService.save(category1)

        and:'a saved budget'
        Budget budget = generateSavedCategoryBudget(user1, category1)

        and:'a update command'
        BudgetUpdateCommand cmd = new BudgetUpdateCommand()
        cmd.with {
            userId = user1.id
            name = 'partially updated'
        }

        and: 'a client'
        HttpRequest request = HttpRequest.PUT("${BUDGETS_ROOT}/${budget.id}", cmd).bearerAuth(accessToken)

        when:
        def resp = client.toBlocking().exchange(request, Argument.of(BudgetDto) as Argument<BudgetDto>,
                Argument.of(ErrorsDto))
        then:
        resp.status == HttpStatus.OK
        resp.body().with {
            assert categoryId == budget.category.id
            assert name == cmd.name
            assert amount == budget.amount
        }

    }

    def "Should not update a budget on band parameters and return Bad Request"() {
        given: 'a saved user'
        User user1 = generateUser()

        and: 'a saved category'
        Category category1 = generateCategory(user1)

        and:'a saved budget'
        Budget budget = generateSavedCategoryBudget(user1, category1)

        HttpRequest request = HttpRequest.PUT("${BUDGETS_ROOT}/${budget.id}", []).bearerAuth(accessToken)

        when:
        client.toBlocking().exchange(request,  Argument.of(BudgetDto) as Argument<BudgetDto>,
                Argument.of(ErrorDto))

        then:
        def e = thrown HttpClientResponseException
        e.response.status == HttpStatus.BAD_REQUEST

    }

    def "Should not update a budget and throw not found exception"() {
        given:
        def notFoundId = 666

        and: 'a client'
        def user = generateUser()
        def category = generateCategory(user)
        HttpRequest request = HttpRequest.PUT("${BUDGETS_ROOT}/${notFoundId}",
                generateBudgetCommand(user, category))
                .bearerAuth(accessToken)

        when:
        client.toBlocking().exchange(request, BudgetDto)

        then:
        def e = thrown HttpClientResponseException
        e.response.status == HttpStatus.NOT_FOUND

    }

    def "Should get a list of budgets"() {

        given:
        User user1 = generateUser()
        User user2 = generateUser()

        Category category1 = generateCategory(user1)

        Budget budget1 = generateSavedCategoryBudget(user1, category1)
        budget1.dateDeleted = new Date()
        budgetGormService.save(budget1)

        Budget budget2 =   generateSavedCategoryBudget(user1, category1)
        Budget budget3 =   generateSavedCategoryBudget(user2, category1)
        Budget budget4 =   generateSavedCategoryBudget(user1, category1)


        and:
        HttpRequest getReq = HttpRequest.GET("$BUDGETS_ROOT?userId=${user1.id}").bearerAuth(accessToken)

        when:
        def rspGET = client.toBlocking().exchange(getReq, Map)

        then:
        rspGET.status == HttpStatus.OK
        Map body = rspGET.getBody(Map).get()
        assert body.get("nextCursor") == null
        List<BudgetDto> budgetDtos = body.data as List<BudgetDto>
        assert !budgetDtos.find {it.id == budget1.id}
        assert budgetDtos.find {it.id == budget2.id}
        assert !budgetDtos.find {it.id == budget3.id}
        assert budgetDtos.find {it.id == budget4.id}
    }

    def "Should get a list of budgets in a cursor "() {

        given: 'a budget list'
        User user1 = generateUser()
        User user2 = generateUser()
        Category category1 = generateCategory(user1)

        Budget budget1 = generateSavedCategoryBudget(user1, category1)
        budget1.dateDeleted = new Date()
        budgetGormService.save(budget1)
        Budget budget2 = generateSavedCategoryBudget(user1, category1)
        Budget budget3 = generateSavedCategoryBudget(user2, category1)
        Budget budget4 = generateSavedCategoryBudget(user1, category1)
        Budget budget5 = generateSavedCategoryBudget(user1, category1)

        and:
        HttpRequest getReq = HttpRequest.GET("$BUDGETS_ROOT?cursor=${budget4.id}&userId=${user1.id}").bearerAuth(accessToken)

        when:
        def rspGET = client.toBlocking().exchange(getReq, Map)

        then:
        rspGET.status == HttpStatus.OK
        Map body = rspGET.getBody(Map).get()
        List<BudgetDto> budgetDtos = body.get("data") as List<BudgetDto>
        assert budgetDtos.size() == 2
        assert body.get("nextCursor") == null
        assert !budgetDtos.find {it.id == budget1.id}
        assert budgetDtos.find {it.id == budget2.id}
        assert !budgetDtos.find {it.id == budget3.id}
        assert budgetDtos.find {it.id == budget4.id}
        assert !budgetDtos.find {it.id == budget5.id}

    }

    def "Should throw not found exception on delete no found budget"() {
        given:
        def notFoundId = 666

        and: 'a client'
        HttpRequest request = HttpRequest.DELETE("${BUDGETS_ROOT}/${notFoundId}").bearerAuth(accessToken)

        when:
        client.toBlocking().exchange(request,
                Argument.of(BudgetDto) as Argument<BudgetDto>,
                Argument.of(ItemNotFoundException))

        then:
        def e = thrown HttpClientResponseException
        e.response.status == HttpStatus.NOT_FOUND

    }

    def "Should delete a budget"() {
        given: 'a saved budget'
        User user1 = generateUser()
        Category category = generateCategory(user1)

        and:' a saved budget'
        Budget budget = generateSavedCategoryBudget(user1,category)

        and: 'a client request'
        HttpRequest request = HttpRequest.DELETE("${BUDGETS_ROOT}/${budget.id}").bearerAuth(accessToken)

        when:
        def response = client.toBlocking().exchange(request, BudgetDto)

        then:
        response.status == HttpStatus.NO_CONTENT

        and:
        HttpRequest.GET("${BUDGETS_ROOT}/${budget.id}").bearerAuth(accessToken)

        when:
        client.toBlocking().exchange(request, Argument.of(BudgetDto) as Argument<BudgetDto>,
                Argument.of(ItemNotFoundException))

        then:
        def e = thrown HttpClientResponseException
        e.response.status == HttpStatus.NOT_FOUND

    }

    def "Should get a budget with transaction analysis"() {

        given:
        User user1 = generateUser()

        Category category1 = generateCategory(user1)

        Budget budget1 = new Budget()
        budget1.with {
            user = user1
            category = category1
            name = 'test budget'
            warningPercentage = 0.7
            amount = 250.00
        }
        budgetGormService.save(budget1)

        and:'a already saved entity with same code'
        FinancialEntity entity = new FinancialEntity()
        entity.with {
            name = 'a saved bank'
            code = 123
            entity.client = loggedInClient
        }
        financialEntityGormService.save(entity)

        and:'a saved account'
        Account account = new Account()
        account.with {
            user = user1
            financialEntity = entity
            nature = 'test'
            name = 'test'
            cardNumber = 1234123412341234
            balance = 0.0
        }
        accountGormService.save(account)

        and:
        Transaction transaction = new Transaction()
        transaction.with {
            transaction.account = account
            executionDate = new Date()
            charge = true
            description = "UBER EATS"
            amount= 200
            category = category1
        }
        transactionGormService.save(transaction)

        and:
        HttpRequest getReq = HttpRequest.GET("$BUDGETS_ROOT?userId=${user1.id}").bearerAuth(accessToken)

        when:
        def rspGET = client.toBlocking().exchange(getReq, Map)

        then:
        rspGET.status == HttpStatus.OK
        Map body = rspGET.getBody(Map).get()
        assert body.get("nextCursor") == null
        List<BudgetDto> budgetDtos = body.data as List<BudgetDto>

        assert budgetDtos
        budgetDtos.first().with {
            assert spent == 200
            assert status == 'warning'
            assert leftToSpend == 50
        }
    }

    private User generateUser() {
        userGormService.save(new User('awesome user', loggedInClient))
    }

    private Category generateCategory(User user1) {
        Category category1 = new Category()
        category1.with {
            user = user1
            name = 'Shoes and clothes'
            color = "#00FFAA"
            category1.client = loggedInClient
        }
        categoryGormService.save(category1)
    }

    private static BudgetCreateCommand generateBudgetCommand(User user, Category category) {
        BudgetCreateCommand cmd = new BudgetCreateCommand()
        cmd.with {
            userId = user.id
            categoryId = category.id
            name = "Food budget"
            amount = 1234.56
        }
        cmd
    }

    private Budget generateSavedCategoryBudget(User user1, Category category1) {
        Budget budget1 = new Budget()
        budget1.with {
            user = user1
            category = category1
            name = 'test budget'
            warningPercentage = 0.7
            amount = 100.00
        }
        budgetGormService.save(budget1)

    }

}