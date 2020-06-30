package mx.finerio.pfm.api.services

import io.micronaut.context.annotation.Property
import io.micronaut.test.annotation.MicronautTest
import mx.finerio.pfm.api.Application
import mx.finerio.pfm.api.domain.Account
import mx.finerio.pfm.api.domain.Category
import mx.finerio.pfm.api.domain.Transaction
import mx.finerio.pfm.api.dtos.CategoryResumeDto
import mx.finerio.pfm.api.dtos.MovementsDto
import spock.lang.Specification
import javax.inject.Inject
import java.text.SimpleDateFormat

@Property(name = 'spec.name', value = 'resume service')
@MicronautTest(application = Application.class)
class ResumeServiceSpec extends Specification {
    public static final int DECEMBER = 11
    @Inject
    ResumeService resumeService

    def "Should Group by month"() {
        given:

        Category parent1 = new Category()
        parent1.with {
            name = 'transporte'
            color = 'red'
            id = 1
        }

        Category sub1 = new Category()
        sub1.with {
            name = 'uber'
            color = 'red'
            parent = parent1
            id = 11
        }

        Category sub2 = new Category()
        sub2.with {
            name = 'viajes'
            color = 'red'
            parent = parent1
            id = 12
        }

        Category parent2 = new Category()
        parent2.with {
            name = 'entretenimiento'
            color = 'red'
            id = 2
        }

        Category sub3 = new Category()
        sub3.with {
            name = 'gaming'
            color = 'red'
            parent = parent2
            id = 23
        }

        Category sub4 = new Category()
        sub4.with {
            name = 'conciertos'
            color = 'red'
            parent = parent2
            id = 24
        }


        Date december98Date = new SimpleDateFormat("dd/MM/yyyy").parse("13/12/1998")
        Date december20Date = new SimpleDateFormat("dd/MM/yyyy").parse("20/12/2020")

        def t1 = generateTransaction(december98Date, sub1)
        def t2 = generateTransaction(december20Date, sub2)
        def t3 = generateTransaction(december20Date, sub3)
        def t4 = generateTransaction(december98Date, sub4)
        def t5 = generateTransaction(december98Date, sub4)
        List<Transaction> transactions = [t1, t2, t3, t4, t5]

        when:
        List<MovementsDto> movementsByMonth = resumeService.getTransactionsGroupByMonth(transactions)

        then:

        assert movementsByMonth.size() == 2
        assert movementsByMonth.first().amount == 300
        def date = new  Date(movementsByMonth.first().date)
        assert date.month == december98Date.month
        assert date.year == december98Date.year
        assert movementsByMonth.first().categories.size() == 3
        movementsByMonth.first().categories.each { dto ->
            assert transactions.stream().anyMatch{it.id == dto["id"]}
        }

        assert movementsByMonth.last().amount == 200
        def date2 = new  Date(movementsByMonth.last().date)
        assert date2.month == december20Date.month
        assert date2.year == december20Date.year
        assert movementsByMonth.last().categories.size() == 2
        movementsByMonth.last().categories.each { dto ->
            assert transactions.stream().anyMatch{it.id == dto["id"]}
        }




    }

    def "Should Group by parent category"() {
        given:
        Category parent1 = new Category()
        parent1.with {
            name = 'transporte'
            color = 'red'
            id = 1
        }

        Category sub1 = new Category()
        sub1.with {
            name = 'uber'
            color = 'red'
            parent = parent1
            id = 11
        }

        Category sub2 = new Category()
        sub2.with {
            name = 'viajes'
            color = 'red'
            parent = parent1
            id = 12
        }

        Category parent2 = new Category()
        parent2.with {
            name = 'entretenimiento'
            color = 'red'
            id = 2
        }

        Category sub3 = new Category()
        sub3.with {
            name = 'gaming'
            color = 'red'
            parent = parent2
            id = 23
        }

        Category sub4 = new Category()
        sub4.with {
            name = 'conciertos'
            color = 'red'
            parent = parent2
            id = 24
        }

        def t1 = generateTransaction(new Date(), sub1)
        def t2 = generateTransaction(new Date(), sub2)
        def t3 = generateTransaction(new Date(), sub3)
        def t4 = generateTransaction(new Date(), sub4)

        List<Transaction> transactions = [t1, t2, t3, t4]

        when:
        List<CategoryResumeDto> movementsByCategory = resumeService.getTransactionsGroupByParentCategory(transactions)

        then:
        assert movementsByCategory.first().categoryId == 1
        assert movementsByCategory.first().amount == 200
        assert movementsByCategory.first().subcategories.size() == 2
        assert !movementsByCategory.first().subcategories.contains( [t1,t2])

        assert movementsByCategory.last().categoryId == 2
        assert movementsByCategory.last().amount == 200
        assert movementsByCategory.last().subcategories.size() == 2
        assert !movementsByCategory.last().subcategories.contains( [t3,t4])
    }

    private static Transaction generateTransactionWithDate(Date date1){
        generateTransaction(date1, new Category())
    }

    private static Transaction generateTransaction(Date date1, Category category1) {
        Transaction transaction = new Transaction()
        transaction.with {
            account = new Account()
            charge = true
            description = 'rapi'
            amount = 100.00
            date = date1
            category = category1
        }
        transaction
    }
}
