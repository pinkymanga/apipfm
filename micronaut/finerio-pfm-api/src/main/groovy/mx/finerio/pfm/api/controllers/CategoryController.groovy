package mx.finerio.pfm.api.controllers

import grails.gorm.transactions.Transactional
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.*
import io.micronaut.security.annotation.Secured
import io.micronaut.validation.Validated
import io.reactivex.Single
import mx.finerio.pfm.api.domain.Category
import mx.finerio.pfm.api.domain.SystemCategory
import mx.finerio.pfm.api.dtos.resource.CategoryDto
import mx.finerio.pfm.api.dtos.resource.ResourcesDto
import mx.finerio.pfm.api.exceptions.BadRequestException
import mx.finerio.pfm.api.logging.Log
import mx.finerio.pfm.api.services.*
import mx.finerio.pfm.api.validation.CategoryCreateCommand
import mx.finerio.pfm.api.validation.CategoryUpdateCommand

import javax.annotation.Nullable
import javax.inject.Inject
import javax.validation.Valid
import javax.validation.constraints.NotNull

@Controller("/categories")
@Validated
@Secured('isAuthenticated()')
class CategoryController {

    @Inject
    CategoryService categoryService

    @Inject
    SystemCategoryService systemCategoryService

    @Inject
    UserService userService

    @Inject
    BudgetService budgetService

    @Inject
    TransactionService transactionService

    @Log
    @Post("/")
    Single<CategoryDto> save(@Body @Valid CategoryCreateCommand cmd){
        Single.just(new CategoryDto(categoryService.create(cmd)))
    }

    @Log
    @Get("/{id}")
    @Transactional
    Single<CategoryDto> show(@NotNull Long id) {
        SystemCategory systemCategory = systemCategoryService.find(id)
        if(systemCategory){
            CategoryDto dto = new CategoryDto()
            dto.with {
                dto.id = systemCategory.id
                name = systemCategory.name
                color = systemCategory.color
                parentCategoryId = systemCategory?.parent?.id
            }
            return Single.just(dto)
        }

        Single.just(new CategoryDto(categoryService.getById(id)))
    }

    @Log
    @Get("{?userId}")
    @Transactional
    Single<ResourcesDto> showAll( @Nullable Long userId) {
        List<CategoryDto> clientCategories = systemCategoryService.findAll()
        clientCategories.addAll( categoryService.findAllByCurrentLoggedClientAndUserNull() )

        if(userId) {
            clientCategories.addAll( categoryService.findAllCategoryDtosByUser(userService.findUser(userId)))
        }

        clientCategories = clientCategories.sort { c1, c2 -> c2.id <=> c1.id }
        Single.just(new ResourcesDto(clientCategories, null))
    }

    @Log
    @Put("/{id}")
    @Transactional
    Single<CategoryDto> edit(@Body @Valid CategoryUpdateCommand cmd, @NotNull Long id ) {
        Single.just(new CategoryDto(categoryService.update(cmd, id)))
    }

    @Log
    @Delete("/{id}")
    @Transactional
    HttpResponse delete(@NotNull Long id) {
        Category category = categoryService.getById(id)
        validateIfSomeOneIsUsingThisCategory(category)
        categoryService.delete(category)
        HttpResponse.noContent()
    }

    private void validateIfSomeOneIsUsingThisCategory(Category category) {
        if (budgetService.findByCategory(category)) {
            throw new BadRequestException('category.budget.existence')
        }
        if (transactionService.findAllByCategory(category)) {
            throw new BadRequestException('category.transaction.existence')
        }

        if (categoryService.findAllByCategory(category)) {
            throw new BadRequestException('category.childCategory.existence')
        }
    }


}
