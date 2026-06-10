package org.example.keibaapp;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class FavoriteJockeyController {

    private final FavoriteJockeyService service;

    public FavoriteJockeyController(FavoriteJockeyService service) {
        this.service = service;
    }

    @PostMapping("/favoriteJockey/add")
    public String addFavorite(@RequestParam String jockeyName) {
        service.save(jockeyName);
        return "redirect:/races";
    }

    @GetMapping("/favoritesJockey")
    public String favorites(Model model) {
        model.addAttribute("favoritesJockey", service.findAll());
        return "favoritesJockey";
    }

    @PostMapping("/favoriteJockey/delete")
    public String deleteFavorite(@RequestParam Long id) {
        service.delete(id);
        return "redirect:/favoritesJockey";
    }
}