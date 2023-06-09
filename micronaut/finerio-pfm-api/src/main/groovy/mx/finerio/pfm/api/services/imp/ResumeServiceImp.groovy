package mx.finerio.pfm.api.services.imp

import grails.gorm.transactions.Transactional
import mx.finerio.pfm.api.domain.Account
import mx.finerio.pfm.api.domain.Transaction
import mx.finerio.pfm.api.dtos.resource.TransactionDto
import mx.finerio.pfm.api.dtos.utilities.*
import mx.finerio.pfm.api.exceptions.BadRequestException
import mx.finerio.pfm.api.services.AccountService
import mx.finerio.pfm.api.services.ResumeService
import mx.finerio.pfm.api.services.TransactionService
import mx.finerio.pfm.api.validation.ResumeFilterParamsCommand

import javax.inject.Inject
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.stream.Collectors

class ResumeServiceImp extends ServiceTemplate implements ResumeService{

    public static final boolean INCOME = false
    public static final boolean EXPENSE = true

    @Inject
    AccountService accountService

    @Inject
    TransactionService transactionsService

    @Override
    @Transactional
    ResumeDto getResume(Long userId, ResumeFilterParamsCommand cmd) {

        Date fromDate = cmd.dateFrom ? validateFromDate(cmd.dateFrom) : getFromLimit()
        Date toDate = cmd.dateTo ? validateToDate(cmd.dateTo , fromDate) : new Date()

        List<Account> accounts = cmd.accountId
                ? [accountService.findAccount(cmd.accountId)]
                : accountService.findAllByUserId(userId)

        List<MovementsResumeDto> incomesResult = resumeTransactionsGroupByMonth(
                transactionsService.getAccountsTransactions(accounts, INCOME, fromDate, toDate))
        List<MovementsResumeDto> expensesResult = getExpensesResume(accounts, fromDate, toDate)

        ResumeDto resumeDto = new ResumeDto()
        resumeDto.incomes = incomesResult
        resumeDto.expenses = expensesResult
        resumeDto.balances =  getBalance(incomesResult, expensesResult)
        resumeDto
    }

    @Override
    @Transactional
    List<MovementsResumeDto> resumeTransactionsGroupByMonth(List<Transaction> transactionList){
        List<MovementsResumeDto> movementsDtoList= []
        Map<String, List<Transaction>> list =  transactionList.stream()
                .collect( Collectors.groupingBy({ Transaction transaction ->
                    new SimpleDateFormat("yyyy-MM").format(transaction.executionDate)
                }))
        for ( Map.Entry<String, List<Transaction>> entry : list.entrySet() ) {
            movementsDtoList.add( generateResumeMovementDto( entry.key, entry.value ) )
        }
        movementsDtoList
    }

    @Override
    @Transactional
    List<MovementsAnalysisDto> analysisTransactionsGroupByMonth(List<Transaction> transactionList){
        List<MovementsAnalysisDto> movementsAnalysisDtos = []
        Map<String, List<Transaction>> list =  transactionList.stream()
                .collect( Collectors.groupingBy({ Transaction transaction ->
                    new SimpleDateFormat("yyyy-MM").format(transaction.executionDate)
                })
                )
        for ( Map.Entry<String, List<Transaction>> entry : list.entrySet() ) {
            movementsAnalysisDtos.add( generateMovementAnalysisDto( entry.key, entry.value ) )
        }
        movementsAnalysisDtos
    }

    @Override
    @Transactional
    List<MovementsResumeDto> getExpensesResume(List<Account> accounts, Date fromDate, Date toDate) {
        resumeTransactionsGroupByMonth(transactionsService.getAccountsTransactions(accounts, EXPENSE, fromDate, toDate))
    }

    @Override
    Date getFromLimit() {
        return Date.from(ZonedDateTime.now().minusMonths(6).toInstant())
    }

    @Override
    Date validateFromDate(Long dateFrom) {
        Date from = new Date(dateFrom)
        if(from.before(getFromLimit())){
            throw new BadRequestException("date.range.invalid")
        }
        from
    }

    @Override
    Date validateToDate(Long dateTo, Date from) {
        Date to =  new Date(dateTo)
        if(to.before(from)){
            throw new BadRequestException("date.range.invalid")
        }
        to
    }

    @Override
    List<BalancesDto> getBalance(List<MovementsResumeDto> incomesResult, List<MovementsResumeDto> expensesResult) {
        def lists = [incomesResult.collect {
            BalancesDto balance =new BalancesDto()
            balance.date = it.date
            balance.incomes = it.amount
            balance
        },
         expensesResult.collect {
             BalancesDto balance =new BalancesDto()
             balance.date = it.date
             balance.expenses = it.amount
             balance
         }]
        Map<Long, List<BalancesDto>>  dateGroupTransactions = lists.flatten().stream()
                .collect ( Collectors.groupingBy({ BalancesDto balancesDto -> balancesDto.date}))

        dateGroupTransactions.collect {  Long dateKey , List<BalancesDto> balances ->
           BalancesDto balancesDto = new BalancesDto()
            balancesDto.with {
                date = dateKey
                incomes = balances*.incomes.sum()
                expenses = balances*.expenses.sum()
            }
            balancesDto
        }
    }

    private static List<BaseCategoryResumeDto> getTransactionsGroupByBaseCategory(
            List<Transaction> transactionList,
            Closure baseCategoryResumeGenerator,
            Closure groupCollector){
        List<BaseCategoryResumeDto> baseCategoryResumeDtos = []

        if(transactionList.isEmpty()) return []


        Map<Long, List<Transaction>> transactionsGrouped = transactionList.stream()
                .collect ( Collectors.groupingBy(groupCollector))

        for ( Map.Entry<Long, List<Transaction>> entry : transactionsGrouped.entrySet() ) {
            baseCategoryResumeDtos.add(baseCategoryResumeGenerator( entry.key, entry.value ) as BaseCategoryResumeDto)
        }
        baseCategoryResumeDtos
    }

    private static List<TransactionDto> getTransactionsGroupByDay(List<Transaction> transactionList){
        Map<String, List<Transaction>> map = transactionList.groupBy { transaction ->
            new SimpleDateFormat("yyyy-MM-dd").format(transaction.executionDate)
        }

        List<TransactionDto> list = []

        for ( Map.Entry<String, List<Transaction>> entry : map.entrySet() ) {
            list << generateTransactionByDate( entry.key, entry.value )
        }

        list
    }

    private static List<TransactionsDescriptionAnalysisDto> getTransactionsGroupByDescription(List<Transaction> transactionList){
        Map<String, List<Transaction>> map = transactionList.groupBy { transaction ->
            transaction.description
        }

        List<TransactionsDescriptionAnalysisDto> list = []

        for ( Map.Entry<String, List<Transaction>> entry : map.entrySet() ) {
            list << generateTransactionByDescription( entry.key, entry.value )
        }

        list
    }

    private static TransactionsDescriptionAnalysisDto generateTransactionByDescription(String descriptionToSet, List<Transaction> transactionList){
        TransactionsDescriptionAnalysisDto descriptionAnalysisDto = new TransactionsDescriptionAnalysisDto()
        descriptionAnalysisDto.description = descriptionToSet
        descriptionAnalysisDto.quantity = transactionList.size()
        descriptionAnalysisDto.amount = transactionList*.amount.sum() as float
        descriptionAnalysisDto.average = descriptionAnalysisDto.amount / descriptionAnalysisDto.quantity
        descriptionAnalysisDto
    }

    private static TransactionsByDateDto generateTransactionByDate(String stringDate, List<Transaction> transactionList){
        TransactionsByDateDto transactionsByDateDto = new TransactionsByDateDto()
        transactionsByDateDto.date = generateDate(stringDate).getTime()
        transactionsByDateDto.transactions = transactionList.collect{new TransactionDto(it)}
        transactionsByDateDto
    }

    private MovementsResumeDto generateResumeMovementDto(String stringDate, List<Transaction> transactions) {

        MovementsResumeDto movementsDto = new MovementsResumeDto()
        movementsDto.date = generateFixedDate(stringDate).getTime()

        List<Transaction> systemCategoryTransactions = transactions.findAll {
            it.systemCategory != null && it.systemCategory.parent != null }

        if(!systemCategoryTransactions.isEmpty()){
            movementsDto.categories.addAll( getTransactionsGroupByBaseCategory(
                    systemCategoryTransactions,
                    generateSystemParentCategoryResume,
                    systemParentCategoryCollector()
            ))
        }

        List<Transaction>  categoryTransactions = transactions.findAll {
            it.category != null  && it.category.parent != null }

        if(!categoryTransactions.isEmpty()){

            movementsDto.categories.addAll( getTransactionsGroupByBaseCategory(
                categoryTransactions,
                generateParentCategoryResume,
                parentCategoryCollector()
            ))
        }



        movementsDto.amount = transactions*.amount.sum() as float
        movementsDto
    }

    private MovementsAnalysisDto generateMovementAnalysisDto(String stringDate, List<Transaction> transactions){
        MovementsAnalysisDto movementsAnalysisDto = new MovementsAnalysisDto()
        movementsAnalysisDto.date = generateFixedDate(stringDate).getTime()

        List<Transaction> systemCategoryTransactions = transactions
                .findAll {it.systemCategory != null }

        if(systemCategoryTransactions){
            movementsAnalysisDto.categories.addAll( getTransactionsGroupByBaseCategory(
                    systemCategoryTransactions,
                    generateSystemParentCategoryAnalysis,
                    systemParentCategoryCollector()
            ))
        }

        List<Transaction>  categoryTransactions = transactions.findAll { it.category != null }
        if(categoryTransactions) {
            movementsAnalysisDto.categories.addAll(getTransactionsGroupByBaseCategory(
                    categoryTransactions,
                    generateParentCategoryAnalysis,
                    parentCategoryCollector()
            ))
        }
        movementsAnalysisDto
    }


    def generateSystemParentCategoryResume = { Long parentId, List<Transaction> transactions ->
        CategoryResumeDto parentCategory = new CategoryResumeDto()
        parentCategory.categoryId = parentId
        parentCategory.subcategories = getTransactionsGroupByBaseCategory(
                transactions, generateSubCategoryResume, systemSubCategoryCollector()
        ) as List<SubCategoryResumeDto>
        parentCategory.amount = transactions*.amount.sum() as float
        parentCategory
    }

    def generateParentCategoryResume = { Long parentId, List<Transaction> transactions ->
        CategoryResumeDto parentCategory = new CategoryResumeDto()
        parentCategory.categoryId = parentId
        parentCategory.subcategories = getTransactionsGroupByBaseCategory(
                transactions, generateSubCategoryResume, subCategoryCollector()
        ) as List<SubCategoryResumeDto>
        parentCategory.amount = transactions*.amount.sum() as float
        parentCategory
    }

    def generateSubCategoryResume =  { Long parentId, List<Transaction> transactions ->
        SubCategoryResumeDto subCategoryResumeDto = new SubCategoryResumeDto()
        subCategoryResumeDto.categoryId = parentId
        subCategoryResumeDto.transactionsByDate = getTransactionsGroupByDay(transactions)
        subCategoryResumeDto.amount = transactions*.amount.sum() as float
        subCategoryResumeDto
    }

    def generateSystemParentCategoryAnalysis = { Long parentId, List<Transaction> transactions ->
        CategoryAnalysisDto parentCategory = new CategoryAnalysisDto()
        parentCategory.categoryId = parentId
        parentCategory.subcategories = getTransactionsGroupByBaseCategory(
                transactions, generateSubCategoryAnalysis, systemSubCategoryCollector()
        ) as List<SubCategoryResumeDto>
        parentCategory.amount = transactions*.amount.sum() as float
        parentCategory
    }

    def generateParentCategoryAnalysis = { Long parentId, List<Transaction> transactions ->
        CategoryAnalysisDto parentCategory = new CategoryAnalysisDto()
        parentCategory.categoryId = parentId
        parentCategory.subcategories = getTransactionsGroupByBaseCategory(
                transactions, generateSubCategoryAnalysis, subCategoryCollector()
        ) as List<SubCategoryResumeDto>
        parentCategory.amount = transactions*.amount.sum() as float
        parentCategory
    }

    def generateSubCategoryAnalysis =  { Long parentId, List<Transaction> transactions ->
        SubCategoryAnalysisDto subCategoryAnalysisDto = new SubCategoryAnalysisDto()
        subCategoryAnalysisDto.categoryId = parentId
        subCategoryAnalysisDto.transactions = getTransactionsGroupByDescription(transactions)
        subCategoryAnalysisDto.amount = transactions*.amount.sum() as float
        subCategoryAnalysisDto.average = subCategoryAnalysisDto.amount / transactions.size()
        subCategoryAnalysisDto.quantity = transactions.size()
        subCategoryAnalysisDto
    }

    private static Closure<Long> parentCategoryCollector() {
        { Transaction transaction ->
            transaction?.category?.parent?.id
        }
    }

    private static Closure<Long> subCategoryCollector() {
        { Transaction transaction ->
            transaction?.category?.id
        }
    }

    private static Closure<Long> systemParentCategoryCollector() {
        { Transaction transaction ->
            transaction?.systemCategory?.parent?.id
        }
    }

    private static Closure<Long> systemSubCategoryCollector() {
        { Transaction transaction ->
            transaction?.systemCategory?.id
        }
    }

    private static Date generateFixedDate(String rawDate){
        generateDate("${rawDate}-01")
    }

    private static Date generateDate(String rawDate) {
        Date.from(LocalDate.parse(rawDate).atStartOfDay(ZoneId.of('America/Mexico_City')).toInstant())
    }

}
