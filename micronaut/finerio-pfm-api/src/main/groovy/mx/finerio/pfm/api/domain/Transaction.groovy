package mx.finerio.pfm.api.domain

import grails.gorm.annotation.Entity
import mx.finerio.pfm.api.validation.TransactionCreateCommand
import org.grails.datastore.gorm.GormEntity

@Entity
class Transaction implements GormEntity<Transaction> {
    Long id
    Account account
    Date date
    boolean charge
    String description
    float  amount
    Date dateCreated
    Date lastUpdated
    Date dateDeleted
    Category category

    Transaction(){}

    Transaction(TransactionCreateCommand cmd, Account account, Category category){
        this.account = account
        this.date = new Date(cmd.date)
        this.charge = cmd.charge
        this.description = cmd.description
        this.amount = cmd.amount
        this.category = category
    }

    static constraints = {
        date nullable: false, blank:false
        description  nullable: false, blank:false
        amount nullable: false
        dateDeleted nullable:true
        category nullable:true
    }

    static mapping = {
        autoTimestamp true
    }
}
