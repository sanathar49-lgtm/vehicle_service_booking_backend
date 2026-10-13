package edu.vehicle.booking;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
@Controller class SpaController {
 @GetMapping({"/{path:[^\\.]*}","/{path:[^\\.]*}/{child:[^\\.]*}"}) String forwardToApp(){return "forward:/index.html";}
}
