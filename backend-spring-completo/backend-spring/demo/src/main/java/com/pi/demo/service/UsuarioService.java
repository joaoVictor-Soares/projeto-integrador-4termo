package com.pi.demo.service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pi.demo.config.SegurancaProperties;
import com.pi.demo.dto.UsuarioRequest;
import com.pi.demo.exception.ConflitoException;
import com.pi.demo.exception.CredenciaisInvalidasException;
import com.pi.demo.exception.OperacaoInvalidaException;
import com.pi.demo.exception.RecursoNaoEncontradoException;
import com.pi.demo.model.Usuario;
import com.pi.demo.repository.PecasRecusadasRepository;
import com.pi.demo.repository.UsuarioRepository;

@Service
public class UsuarioService {

    public static final String ADMIN = "ADMIN";
    public static final String OPERADOR = "OPERADOR";
    private static final Set<String> PERFIS = Set.of(ADMIN, OPERADOR);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.?[^@\\s]*$");
    private static final int SENHA_MINIMA = 8;
    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);

    private final UsuarioRepository repository;
    private final PecasRecusadasRepository pecasRepository;
    private final PasswordEncoder encoder;
    private final SegurancaProperties props;
    /** Hash descartavel: igualar o custo do login com e-mail inexistente (evita enumerar usuarios pelo tempo). */
    private final String hashFalso;

    public UsuarioService(UsuarioRepository repository,
                          PecasRecusadasRepository pecasRepository,
                          PasswordEncoder encoder,
                          SegurancaProperties props) {
        this.repository = repository;
        this.pecasRepository = pecasRepository;
        this.encoder = encoder;
        this.props = props;
        this.hashFalso = encoder.encode("senha-inexistente");
    }

    @Transactional(readOnly = true)
    public List<Usuario> listar() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Usuario buscar(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuario " + id + " nao encontrado."));
    }

    @Transactional
    public Usuario criar(UsuarioRequest req) {
        String nome = exigir(req.nome(), "nome");
        String email = validarEmail(req.email());
        String perfil = validarPerfil(exigir(req.perfil(), "perfil"));
        validarSenha(req.senha());
        if (repository.existsByEmailIgnoreCase(email)) {
            throw new ConflitoException("Ja existe um usuario com o e-mail " + email + ".");
        }
        Usuario u = new Usuario();
        u.setNome(nome.trim());
        u.setEmail(email);
        u.setPerfil(perfil);
        u.setSenhaHash(encoder.encode(req.senha()));
        return repository.save(u);
    }

    @Transactional
    public Usuario atualizar(Integer id, UsuarioRequest req) {
        Usuario u = buscar(id);
        if (req.nome() != null) {
            u.setNome(exigir(req.nome(), "nome").trim());
        }
        if (req.email() != null) {
            String email = validarEmail(req.email());
            repository.findByEmailIgnoreCase(email).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
                throw new ConflitoException("Ja existe um usuario com o e-mail " + email + ".");
            });
            u.setEmail(email);
        }
        if (req.perfil() != null) {
            String novo = validarPerfil(req.perfil());
            if (ADMIN.equals(u.getPerfil()) && !ADMIN.equals(novo) && contarAdmins() <= 1) {
                throw new OperacaoInvalidaException("Nao e possivel rebaixar o unico administrador.");
            }
            u.setPerfil(novo);
        }
        if (req.senha() != null) {
            validarSenha(req.senha());
            u.setSenhaHash(encoder.encode(req.senha()));
        }
        return repository.save(u);
    }

    @Transactional
    public void remover(Integer id, Integer solicitanteId) {
        Usuario u = buscar(id);
        if (id.equals(solicitanteId)) {
            throw new OperacaoInvalidaException("Voce nao pode remover o proprio usuario.");
        }
        if (ADMIN.equals(u.getPerfil()) && contarAdmins() <= 1) {
            throw new OperacaoInvalidaException("Nao e possivel remover o unico administrador.");
        }
        if (pecasRepository.existsByOperadorId(id)) {
            throw new ConflitoException("O usuario " + id + " esta vinculado a pecas recusadas e nao pode ser removido.");
        }
        repository.delete(u);
    }

    /** Valida e-mail/senha. Mesma mensagem para e-mail inexistente e senha errada. */
    @Transactional(readOnly = true)
    public Usuario autenticar(String email, String senha) {
        Usuario u = email == null ? null : repository.findByEmailIgnoreCase(email.trim()).orElse(null);
        String hash = u == null ? hashFalso : u.getSenhaHash();
        boolean ok = senha != null && hash != null && encoder.matches(senha, hash);
        if (u == null || !ok) {
            throw new CredenciaisInvalidasException("E-mail ou senha invalidos.");
        }
        return u;
    }

    @Transactional
    public void alterarSenha(Integer id, String senhaAtual, String novaSenha) {
        Usuario u = buscar(id);
        if (senhaAtual == null || !encoder.matches(senhaAtual, u.getSenhaHash())) {
            throw new CredenciaisInvalidasException("Senha atual incorreta.");
        }
        validarSenha(novaSenha);
        u.setSenhaHash(encoder.encode(novaSenha));
        repository.save(u);
    }

    /** Se nao existe nenhum usuario, cria o administrador inicial (senha do ambiente ou aleatoria no log). */
    @Transactional
    public void garantirAdminInicial() {
        if (repository.count() > 0) {
            return;
        }
        String senha = props.adminSenha();
        boolean gerada = senha == null || senha.isBlank();
        if (gerada) {
            senha = senhaAleatoria();
        }
        Usuario admin = new Usuario();
        admin.setNome(props.adminNome());
        admin.setEmail(props.adminEmail().trim().toLowerCase());
        admin.setPerfil(ADMIN);
        admin.setSenhaHash(encoder.encode(senha));
        repository.save(admin);
        if (gerada) {
            log.warn("==================================================================");
            log.warn(" Administrador inicial criado: {}", admin.getEmail());
            log.warn(" Senha (aparece so agora, troque em /api/auth/senha): {}", senha);
            log.warn("==================================================================");
        } else {
            log.info("Administrador inicial criado: {}", admin.getEmail());
        }
    }

    // ------------------------------------------------------------------

    private long contarAdmins() {
        return repository.findAll().stream().filter(x -> ADMIN.equals(x.getPerfil())).count();
    }

    private static String exigir(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new OperacaoInvalidaException("O campo '" + campo + "' e obrigatorio.");
        }
        return valor;
    }

    private static String validarEmail(String email) {
        String e = exigir(email, "email").trim().toLowerCase();
        if (e.length() > 254 || !EMAIL.matcher(e).matches()) {
            throw new OperacaoInvalidaException("E-mail invalido.");
        }
        return e;
    }

    private static String validarPerfil(String perfil) {
        String p = perfil.trim().toUpperCase();
        if (!PERFIS.contains(p)) {
            throw new OperacaoInvalidaException("Perfil invalido: use OPERADOR ou ADMIN.");
        }
        return p;
    }

    private static void validarSenha(String senha) {
        if (senha == null || senha.length() < SENHA_MINIMA) {
            throw new OperacaoInvalidaException("A senha deve ter pelo menos " + SENHA_MINIMA + " caracteres.");
        }
        if (senha.length() > 72) { // limite do BCrypt
            throw new OperacaoInvalidaException("A senha deve ter no maximo 72 caracteres.");
        }
    }

    private static String senhaAleatoria() {
        String alfabeto = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            sb.append(alfabeto.charAt(r.nextInt(alfabeto.length())));
        }
        return sb.toString();
    }
}
