package com.salonbooking.booking.web;

import com.salonbooking.booking.app.AgendaService;
import com.salonbooking.booking.app.AgendaService.AgendaItem;
import com.salonbooking.identity.app.StaffPrincipal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class StaffAgendaController {

    private final AgendaService agenda;

    StaffAgendaController(AgendaService agenda) {
        this.agenda = agenda;
    }

    @GetMapping("/api/v1/staff/agenda")
    List<AgendaItem> day(@AuthenticationPrincipal StaffPrincipal staff, @RequestParam LocalDate date,
            @RequestParam(required = false) UUID locationId) {
        return agenda.day(staff, date, locationId);
    }
}
