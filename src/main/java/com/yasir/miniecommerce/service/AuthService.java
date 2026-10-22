    package com.yasir.miniecommerce.service;

    import com.yasir.miniecommerce.dto.LoginRequest;
    import com.yasir.miniecommerce.dto.LoginResponse;
    import com.yasir.miniecommerce.dto.RegisterRequest;
    import com.yasir.miniecommerce.dto.RegisterResponse;
    import com.yasir.miniecommerce.exception.DuplicateEmailException;
    import com.yasir.miniecommerce.exception.InvalidCredentialsException;
    import com.yasir.miniecommerce.model.User;
    import com.yasir.miniecommerce.repository.UserRepository;
    import org.springframework.stereotype.Service;
    import org.springframework.security.crypto.password.PasswordEncoder;

    @Service
    public class AuthService {

        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtService jwtService;

        public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
            this.userRepository = userRepository;
            this.passwordEncoder = passwordEncoder;
            this.jwtService = jwtService;


        }

        public RegisterResponse register(RegisterRequest request) {
            if (userRepository.findByEmail(request.getEmail()).isPresent()) {
                throw new DuplicateEmailException("Bu e-posta adresi zaten kullanılıyor");
            }
            User user = new User();

            user.setEmail(request.getEmail());
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.setRole("USER");

            User savedUser = userRepository.save(user);

            RegisterResponse response = new RegisterResponse();

            response.setId(savedUser.getId());
            response.setEmail(savedUser.getEmail());
            response.setRole(savedUser.getRole());

            return response;
        }



        public LoginResponse login(LoginRequest request) {

            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() ->
                            new InvalidCredentialsException("E-posta veya şifre hatalı"));

            if (!passwordEncoder.matches(
                    request.getPassword(),
                    user.getPassword())) {

                throw new InvalidCredentialsException("E-posta veya şifre hatalı");
            }

            String token = jwtService.generateToken(user.getEmail());

            LoginResponse response = new LoginResponse();
            response.setToken(token);

            return response;
        }

    }