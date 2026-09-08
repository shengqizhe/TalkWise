package com.example.lecture.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.RestController;

@SpringBootConfiguration
@RestController
public class EmailController {

    @Autowired
    private JavaMailSender javaMailSender;

    /**
     * 发送不带附件的邮件
     *
     * @param from
     * @param to
     * @param cc
     * @param subject
     * @param text
     */
    public void sendMail(String from, String to, String cc, String subject, String text) {
        SimpleMailMessage smm = new SimpleMailMessage();
        smm.setFrom(from); // 发送者
        smm.setTo(to); // 收件人
        smm.setCc(cc); // 抄送人
        smm.setSubject(subject); // 邮件主题
        smm.setText(text); // 邮件内容
        javaMailSender.send(smm); // 发送邮件
    }
}