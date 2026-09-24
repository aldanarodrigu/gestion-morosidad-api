package com.intendencia.gestion_morosidad_api.modules.usuario;

import com.intendencia.gestion_morosidad_api.modules.usuario.dto.UsuarioRequest;
import com.intendencia.gestion_morosidad_api.modules.usuario.dto.UsuarioResponse;
import com.intendencia.gestion_morosidad_api.modules.usuario.dto.UsuarioUpdateRequest;
import jakarta.validation.Valid;
import org.apache.coyote.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    // GET /api/usuarios
    @GetMapping
    public ResponseEntity<List<UsuarioResponse>> getAll() {
        return ResponseEntity.ok(usuarioService.getAllUsers());
    }

    // GET /api/usuarios/1
    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponse>getById(@PathVariable Long id){
        return ResponseEntity.ok(usuarioService.getUsuarioById(id));
    }

    // POST /api/usuarios/1
    @PostMapping("/{id}")
    public ResponseEntity<UsuarioResponse>create(@Valid @RequestBody UsuarioRequest usuarioRequest){
        UsuarioResponse response = usuarioService.saveUser(usuarioRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    //PUT /api/usuarios/1
    @PutMapping("/{id}")
    public ResponseEntity<UsuarioResponse> update(@PathVariable Long id, @Valid @RequestBody UsuarioUpdateRequest usuarioUpdateRequest) {
        return ResponseEntity.ok(usuarioService.updateUser(id, usuarioUpdateRequest));
    }
}