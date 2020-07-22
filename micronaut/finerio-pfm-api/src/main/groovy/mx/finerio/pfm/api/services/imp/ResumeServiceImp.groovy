package mx.finerio.pfm.api.services.imp

import grails.gorm.transactions.Transactional
import mx.finerio.pfm.api.domain.Transaction
import mx.finerio.pfm.api.dtos.BalancesDto
import mx.finerio.pfm.api.dtos.CategoryResumeDto
import mx.finerio.pfm.api.dtos.MovementsDto
import mx.finerio.pfm.api.dtos.ResumeDto
import mx.finerio.pfm.api.dtos.SubCategoryResumeDto
import mx.finerio.pfm.api.dtos.TransactionDto
import mx.finerio.pfm.api.dtos.TransactionsByDateDto
import mx.finerio.pfm.api.services.AccountService
import mx.finerio.pfm.api.services.ResumeService
import  mx.finerio.pfm.api.services.TransactionService
import mx.finerio.pfm.api.services.gorm.TransactionGormService

import javax.inject.Inject
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.function.Function
import java.util.stream.Collectors
import java.util.stream.Stream

class ResumeServiceImp implements ResumeService{

    @Inject
    AccountService accountService

    @Inject
    TransactionService transactionsService

    @Override
    @Transactional
    List<Transaction> getExpenses(Long userId) {
        getAccountsTransactions(userId, true)
    }

    @Override
    @Transactional
    List<Transaction> getIncomes(Long userId) {
        getAccountsTransactions(userId, false)
    }

    @Override
    @Transactional
    List<MovementsDto> getTransactionsGroupByMonth(List<Transaction> transactionList){
        List<MovementsDto> movementsDtoList= []
        Map<String, List<Transaction>> list =  transactionList.stream()
               .collect( Collectors.groupingBy({ Transaction transaction ->
                    new SimpleDateFormat("yyyy-MM").format(transaction.date)
                }))

        list.each { String stringDate, List<Transaction> transactions ->
            movementsDtoList.add(generateMovementDto(stringDate, transactions))
        }
        movementsDtoList
    }

    @Override
    @Transactional
    ResumeDto getResume(Long userId) {

        List<MovementsDto> incomesResult = getTransactionsGroupByMonth(getIncomes(userId))
        List<MovementsDto> expensesResult = getTransactionsGroupByMonth( getExpenses(userId))

        ResumeDto resumeDto = new ResumeDto()
        resumeDto.with {
            incomes = incomesResult
            expenses = expensesResult
            balances =  getBalance(incomesResult, expensesResult)
        }
        resumeDto
    }

    @Override
    List<BalancesDto> getBalance(List<MovementsDto> incomesResult, List<MovementsDto> expensesResult) {
        [incomesResult.collect {
            [date: it.date, incomes: it.amount]
        },
         expensesResult.collect {
             [date: it.date, expenses: it.amount]
         }].transpose()*.sum() as List<BalancesDto>
    }

    private List<CategoryResumeDto> getTransactionsGroupByParentCategory(List<Transaction> transactionList){
        List<CategoryResumeDto> categoryResumeDtos = []
        Map<Long, List<Transaction>> transactionsGrouped = transactionList.stream()
                .collect ( Collectors.groupingBy({ Transaction transaction ->
                    transaction.category.parent.id
                }))
        transactionsGrouped.each{ Long parentId , List<Transaction> transactions ->
            categoryResumeDtos.add( generateParentCategoryResume(parentId, transactions) )
        }
        categoryResumeDtos
    }

    List<SubCategoryResumeDto> getTransactionsGroupBySubCategory(List<Transaction> transactionList){
        List<SubCategoryResumeDto> subCategoryResumeDtos = []
        Map<Long, List<Transaction>> transactionsGrouped = transactionList.stream()
                .collect ( Collectors.groupingBy({ Transaction transaction ->
            transaction.category.id
        }))
         transactionsGrouped.each { parentId , transactions ->
             subCategoryResumeDtos.add( generateSubCategoryResume(parentId, transactions))
        }
        subCategoryResumeDtos
    }

    private List<TransactionsByDateDto> getTransactionsGroupByDay(List<Transaction> transactionList){
        transactionList.groupBy { transaction ->
            new SimpleDateFormat("yyyy-MM-dd").format(transaction.date)
        }.collect{ stringDate , transactions ->
            generateTransactionByDate(stringDate, transactions)
        }
    }

    private TransactionsByDateDto generateTransactionByDate(String stringDate, List<Transaction> transactionList){
        TransactionsByDateDto transactionsByDateDto = new TransactionsByDateDto()
        transactionsByDateDto.with {
            date = generateDate(stringDate).getTime()
            transactions = transactionList.collect{new TransactionDto(it)}
        }
        transactionsByDateDto
    }

    private MovementsDto generateMovementDto(String stringDate, List<Transaction> transactions) {
        MovementsDto movementsDto = new MovementsDto()
        movementsDto.with {
            date = generateFixedDate(stringDate).getTime()
            categories = getTransactionsGroupByParentCategory( transactions)
            amount = transactions*.amount.sum() as float
        }
        movementsDto
    }

    private CategoryResumeDto generateParentCategoryResume(Long parentId, List<Transaction> transactions) {
        CategoryResumeDto parentCategory = new CategoryResumeDto()
        parentCategory.with {
            categoryId = parentId
            subcategories = getTransactionsGroupBySubCategory(transactions)
            amount = transactions*.amount.sum() as float
        }
        parentCategory
    }

    private SubCategoryResumeDto generateSubCategoryResume(Long parentId, List<Transaction> transactions) {
        SubCategoryResumeDto subCategoryResumeDto = new SubCategoryResumeDto()
        subCategoryResumeDto.with {
            categoryId = parentId
            transactionsByDate = getTransactionsGroupByDay(transactions)
            amount = transactions*.amount.sum() as float
        }
        subCategoryResumeDto
    }

    private static Date generateFixedDate(String rawDate){
        generateDate("${rawDate}-01")
    }

    private static Date generateDate(String rawDate) {
        Date.from(LocalDate.parse(rawDate).atStartOfDay(ZoneId.systemDefault()).toInstant())
    }

    private List<Transaction> getAccountsTransactions(Long userId, Boolean charge) {
        List<Transaction> transactions = []
        accountService.findAllByUserId(userId).each { account ->
            transactions.addAll(transactionsService.findAllByAccountAndCharge(account, charge))
        }
        transactions
    }

}
