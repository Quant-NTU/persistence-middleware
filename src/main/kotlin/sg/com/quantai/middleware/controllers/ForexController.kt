package sg.com.quantai.middleware.controller;

import org.springframework.beans.factory.annotation.Autowired; //Not sure about this
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sg.com.quantai.middleware.data.Forex;
import sg.com.quantai.middleware.repository.ForexRepository; //Needs to be created to retrieve forex data

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/forex")
public class ForexController {

    @Autowired
    private ForexRepository forexRepository;

    // GET: Fetch all Forex records
    @GetMapping
    public ResponseEntity<List<Forex>> getAllForex() {
        List<Forex> forexList = forexRepository.findAll();
        return ResponseEntity.ok(forexList);
    }

    // GET: Fetch a single Forex record by UID
    @GetMapping("/{uid}")
    public ResponseEntity<Forex> getForexByUid(@PathVariable String uid) {
        Optional<Forex> forex = forexRepository.findByUid(uid);
        return forex.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    // POST: Create a new Forex record
    @PostMapping
    public ResponseEntity<Forex> createForex(@RequestBody Forex forex) {
        if (forexRepository.existsByUid(forex.getUid())) {
            return ResponseEntity.badRequest().body(null);
        }
        Forex savedForex = forexRepository.save(forex);
        return ResponseEntity.ok(savedForex);
    }

    // PUT: Update an existing Forex record by UID
    @PutMapping("/{uid}")
    public ResponseEntity<Forex> updateForex(@PathVariable String uid, @RequestBody Forex updatedForex) {
        Optional<Forex> existingForex = forexRepository.findByUid(uid);
        if (existingForex.isPresent()) {
            Forex forex = existingForex.get();
            forex.setName(updatedForex.getName());
            forex.setSymbol(updatedForex.getSymbol());
            forex.setQuantity(updatedForex.getQuantity());
            forex.setPurchasePrice(updatedForex.getPurchasePrice());
            Forex savedForex = forexRepository.save(forex);
            return ResponseEntity.ok(savedForex);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // DELETE: Remove a Forex record by UID
    @DeleteMapping("/{uid}")
    public ResponseEntity<Void> deleteForex(@PathVariable String uid) {
        Optional<Forex> forex = forexRepository.findByUid(uid);
        if (forex.isPresent()) {
            forexRepository.delete(forex.get());
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
