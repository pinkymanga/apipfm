package mx.finerio.pfm.api.dtos.utilities

import groovy.transform.ToString

@ToString(includeNames = true, includePackage = false)
class CategoryResumeDto extends BaseCategoryResumeDto {
    List<SubCategoryResumeDto> subcategories
}
