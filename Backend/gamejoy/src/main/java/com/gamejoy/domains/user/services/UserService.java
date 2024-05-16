package com.gamejoy.domains.user.services;

import com.gamejoy.domains.user.repositories.AddressRepository;
import com.gamejoy.domains.user.dtos.CredentialDto;
import com.gamejoy.domains.user.dtos.SignUpDto;
import com.gamejoy.domains.user.dtos.UserDto;
import com.gamejoy.domains.user.entities.Address;
import com.gamejoy.domains.user.entities.User;
import com.gamejoy.domains.user.entities.UserRole;
import com.gamejoy.domains.general.exceptions.AppException;
import com.gamejoy.domains.general.exceptions.NotFoundException;
import com.gamejoy.domains.user.mappers.UserMapper;
import com.gamejoy.domains.user.repositories.UserRepository;
import com.gamejoy.domains.userIngameCurrency.services.UserIngameCurrencyService;
import lombok.AllArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.gamejoy.config.validation.PasswordValidator;

import java.nio.CharBuffer;
import java.util.Optional;

@Service
@AllArgsConstructor
public class UserService {
    private static final Logger LOGGER = LogManager.getLogger(UserService.class);
    private final UserRepository userRepository;
    private final UserIngameCurrencyService userIngameCurrencyService;
    private final AddressRepository addressRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final PasswordValidator passwordValidator;

    public UserDto login(CredentialDto credentialDto) {
        User user = userRepository.findByUserName(credentialDto.userName())
                .orElseThrow(() -> new AppException("Unknown User", HttpStatus.NOT_FOUND));

        if (passwordEncoder.matches(CharBuffer.wrap(credentialDto.password()),
                user.getPassword())) {
            LOGGER.info(String.format("User %s logged in", user.getUserName()));
            return userMapper.toUserDto(user);
        }
        throw new AppException("Invalid password", HttpStatus.BAD_REQUEST);
    }

    public UserDto register(SignUpDto signUpDto) {
        Optional<User> oUser = userRepository.findByUserName(signUpDto.userName());

        if (oUser.isPresent()) {
            throw new AppException("User already exists", HttpStatus.BAD_REQUEST);
        }
        // Validierung des Passworts
        if (!passwordValidator.isValid(new String(signUpDto.password()), null)) {
            throw new AppException("Invalid password", HttpStatus.BAD_REQUEST);
        }


        User user = userMapper.signUpToUser(signUpDto);
        // encode password with passwordEncoder
        user.setPassword(passwordEncoder.encode(CharBuffer.wrap(signUpDto.password())));
        Address savedAddress = addressRepository.save(user.getAddress());
        user.setAddress(savedAddress);
        user.setUserRole(UserRole.USER);
        User savedUser = userRepository.save(user);
        LOGGER.info(String.format("User %s registered", user.getUserName()));

        //todo: für jeden erstellten benutzer müssen initalwerte in der user_ingame_currencies table angelegt werden
        userIngameCurrencyService.createInitialDataForUserIngameCurrencies(user.getId());

        return userMapper.toUserDto(savedUser);
    }

    public String changeUsername(long id, String username) {
        Optional<User> userO = userRepository.findById(id);

        if (userO.isPresent()) {
            User user = userRepository.save(userO.get());

            String responseText = String.format("Username for User %s with id %d changed",user.getUserName(), id);
            LOGGER.info(responseText);
            return responseText;
        } else {
            throw new NotFoundException(String.format("User with %d not found", id));
        }
    }

    public String changePassword(long id, char[] password) {
        Optional<User> userO = userRepository.findById(id);
        User user = null;

        if (userO.isPresent()) {
            user = userO.get();
            user.setPassword(passwordEncoder.encode(CharBuffer.wrap(password)));
            userRepository.save(user);

            String responseText = String.format("Password for User %s with id %d changed",user.getUserName(), id);
            LOGGER.info(responseText);
            return responseText;
        } else {
            throw new NotFoundException(String.format("User with %d not found", id));
        }
    }
}
