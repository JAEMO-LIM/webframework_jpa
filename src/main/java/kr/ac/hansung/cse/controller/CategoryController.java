package kr.ac.hansung.cse.controller;

import jakarta.validation.Valid;
import kr.ac.hansung.cse.exception.DuplicateCategoryException;
import kr.ac.hansung.cse.model.Category;
import kr.ac.hansung.cse.model.CategoryForm;
import kr.ac.hansung.cse.service.CategoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/categories")
public class CategoryController {
    private final CategoryService categoryService;

    @GetMapping
    public String listCategories(Model model) {
        model.addAttribute("categories", categoryService.getAllCategories());
        return "categoryList";
    }

    @GetMapping("/create")
    public String showCreateCategoryForm(Model model) {
        model.addAttribute("categoryForm", new CategoryForm());
        return "categoryForm";
    }

    @PostMapping("/create")
    public String createCategory(@Valid @ModelAttribute CategoryForm categoryForm, BindingResult bindingResult,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "categoryForm";
        }
        try {
            categoryService.createCategory(categoryForm.getName());
            redirectAttributes.addFlashAttribute("successMessage", "등록 완료");
        } catch (DuplicateCategoryException e) {
            bindingResult.rejectValue("name", "duplicate", e.getMessage());
            return "categoryForm";
        }

        return "redirect:/categories";
    }

    @PostMapping("/{id}/delete")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            categoryService.deleteCategory(id);
            redirectAttributes.addFlashAttribute("successMessage", "삭제 완료");
        }catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/categories";
    }



    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

}
