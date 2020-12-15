package mx.finerio.pfm.api.domain

import grails.gorm.annotation.Entity

import groovy.transform.ToString

import mx.finerio.pfm.api.validation.AccountCreateCommand

import org.grails.datastore.gorm.GormEntity

@Entity
@ToString(includeNames = true, includePackage = false)
class Account  implements GormEntity<Account> {

    Long id
    User user
    FinancialEntity financialEntity
    String nature
    String name
    String number
    float balance
    Date dateCreated
    Date lastUpdated
    Date dateDeleted
    boolean chargeable

    Account(AccountCreateCommand accountCommand, User user, FinancialEntity financialEntity) {
        this.user = user
        this.financialEntity = financialEntity
        this.nature = accountCommand.nature
        this.name = accountCommand.name
        this.number = accountCommand.number
        this.chargeable = accountCommand.chargeable
        this.balance = accountCommand.balance.setScale(
            2, BigDecimal.ROUND_HALF_UP )
    }

    Account(){}

    static constraints = {
        name nullable: false, blank:false
        financialEntity nullable: false
        nature  nullable: false, blank:false
        number nullable: false, blank:false
        balance nullable: false
        dateDeleted nullable:true
    }

    static mapping = {
        autoTimestamp true
    }

}
