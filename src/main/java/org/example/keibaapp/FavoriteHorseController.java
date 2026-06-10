package org.example.keibaapp;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class FavoriteHorseController {

    private final FavoriteHorseService service;

    public FavoriteHorseController(FavoriteHorseService service) {
        this.service = service;
    }

    @PostMapping("/favorite/add")
    public String addFavorite(@RequestParam String horseName) {
        service.save(horseName);
        return "redirect:/races";
    }

    @GetMapping("/favorites")
    public String favorites(Model model) {
        model.addAttribute("favorites", service.findAll());
        return "favorites";
    }

    @PostMapping("/favorite/delete")
    public String deleteFavorite(@RequestParam Long id) {
        service.delete(id);
        return "redirect:/favorites";
    }
}